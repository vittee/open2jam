package org.open2jam.render;

import java.net.URL;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedList;
import javax.xml.parsers.SAXParserFactory;

import org.open2jam.parser.Event.Channel;

import org.open2jam.parser.ResourcesHandler;
import org.open2jam.parser.Chart;
import org.open2jam.parser.Event;
import org.open2jam.render.entities.BPMEntity;
import org.open2jam.render.entities.Entity;
import org.open2jam.render.entities.LongNoteEntity;
import org.open2jam.render.entities.MeasureEntity;
import org.open2jam.render.entities.NoteEntity;
import org.open2jam.render.entities.SampleEntity;
import org.open2jam.render.lwjgl.SoundManager;
import org.open2jam.Util;
import org.open2jam.render.entities.AnimatedEntity;

public class Render implements GameWindowCallback
{
    /** the number of keys */
    private final int NUM_KEYS = 7;

    /** horizontal distance from the left side of screen */
    private final int screen_x_offset = 30;

    /** store the sources being played */
    private final int MAX_SOURCES = 32;

    /** the config xml */
    private static final URL resources_xml = Render.class.getResource("/resources/resources.xml");

    /** The window that is being used to render the game */
    private final GameWindow window;

    /** the chart being rendered */
    private final Chart chart;

    /** the rank of the chart */
    private final int rank;

    /** the hispeed */
    private final double hispeed;

    /** map of sprites to use */
    private Map<String,SpriteList> sprite_map;

    /** the vertical space of the entities */
    private double viewport;

    /** the size of a measure */
    private double measure_size;

    /** pre-built offset of the notes horizontal position */
    private EnumMap<Event.Channel,Integer> channel_x_offset;

    /** the vertical speed of entities pixels/milliseconds */
    private double note_speed;

    /** the screen offset of the buffer */
    private double buffer_offset;

    /** maps the Event value to OpenGL sample ID's */
    private Map<Integer, Integer> samples;

    private Iterator<Event> buffer_iterator;
    private EnumMap<Channel, LongNoteEntity> ln_buffer;

    /** the bpm at which the entities are falling */
    private double bpm;

    /** a list of list of entities.
    ** basically, each list is a layer of entities
    ** the layers are rendered in order
    ** so entities at layer X will always be rendered before layer X+1 */
    private List<List<Entity>> entities_matrix;
    private AnimatedEntity judgment_line;

    /** The recorded fps */
    private int fps;

    private ArrayDeque<Integer> source_queue;
    private LinkedList<Integer> sources_playing;


    /** The time at which the last rendering looped started from the point of view of the game logic */
    private long lastLoopTime;

    /** The time since the last record of fps */
    private long lastFpsTime = 0;

    static{
        ResourceFactory.get().setRenderingType(ResourceFactory.OPENGL_JOGL);
    }

    public Render(Chart c, int rank, double hispeed)
    {
        this.chart = c;
        this.rank = rank;
        this.hispeed = hispeed;
        window = ResourceFactory.get().getGameWindow();
    }
        
    public void setDisplay(int width, int height, boolean vsync, boolean fs) throws Exception{
        window.setDisplay(width, height,vsync,fs);
    }

    public void startRendering(){
        window.setGameWindowCallback(this);
        window.setTitle("Render");
        window.startRendering();
    }

    /**
    * initialize the common elements for the game.
    * this is called by the window render
    */
    public void initialise()
    {
        viewport = 0.8 * window.getResolutionHeight();
        measure_size = 0.8 * hispeed * viewport;
        buffer_offset = viewport;
        setBPM(chart.getBPM(rank));

        entities_matrix = new ArrayList<List<Entity>>();
        entities_matrix.add(new ArrayList<Entity>()); // layer 0 -- measure marks
        entities_matrix.add(new ArrayList<Entity>()); // layer 1 -- notes

        SpriteBuilder sb = new SpriteBuilder();
        try {
            SAXParserFactory.newInstance().newSAXParser().parse(resources_xml.openStream(),new ResourcesHandler(sb));
        } catch (Exception e) {
            Util.die(e);
        }
        sprite_map = sb.getResult();

        // build long note buffer
        ln_buffer = new EnumMap<Event.Channel,LongNoteEntity>(Event.Channel.class);

        // build the notes horizontal offset
        channel_x_offset = new EnumMap<Event.Channel,Integer>(Event.Channel.class);
        int off = screen_x_offset;
        for(Event.Channel c : Event.note_channels)
        {
            channel_x_offset.put(c, off);
            off += sprite_map.get("HEAD_"+c).get(0).getWidth();
        }

        // load up initial buffer
        buffer_iterator = chart.getEvents(rank).iterator();
        update_note_buffer();

        judgment_line = new AnimatedEntity(sprite_map.get("JUDGMENT_LINE"), screen_x_offset, viewport);

        // create sound sources
        source_queue = new ArrayDeque<Integer>(MAX_SOURCES);
        sources_playing = new LinkedList<Integer>();

        try{
        for(int i=0;i<MAX_SOURCES;i++)source_queue.push(SoundManager.newSource()); // creates 32 sources
        }catch(Exception e){Util.warn("Couldn't create enough sources("+MAX_SOURCES+")");}

        // get the chart sound samples
        samples = chart.getSamples(rank);

        lastLoopTime = SystemTimer.getTime();
    }
    
    /**
    * Notification that a frame is being rendered. Responsible for
    * running game logic and rendering the scene.
    */
    public void frameRendering()
    {
        //SystemTimer.sleep(10);
        
        // work out how long its been since the last update, this
        // will be used to calculate how far the entities should
        // move this loop
        long delta = SystemTimer.getTime() - lastLoopTime;
        lastLoopTime = SystemTimer.getTime();
        lastFpsTime += delta;
        fps++;
        
        // update our FPS counter if a second has passed
        if (lastFpsTime >= 1000) {
            window.setTitle("Render (FPS: "+fps+")");
            lastFpsTime = 0;
            fps = 0;
        }

        check_sources();
        update_note_buffer();

        judgment_line.move(delta);
        judgment_line.draw();

        Iterator<List<Entity>> i = entities_matrix.iterator();
        while(i.hasNext()) // loop over layers
        {
            // get entity iterator from layer
            Iterator<Entity> j = i.next().iterator();
            while(j.hasNext()) // loop over entities
            {
                Entity e = j.next();
                e.move(delta); // move the entity

                if(e.getBounds().getY() > viewport)e.judgment();
                if(!e.isAlive())j.remove(); // if dead, remove from list
                else e.draw(); // or draw itself on screen
            }
        }
        buffer_offset += note_speed * delta; // walk with the buffer

        if(!buffer_iterator.hasNext() && entities_matrix.get(1).isEmpty() && sources_playing.isEmpty()){
            window.destroy();
                        windowClosed();
            return;
        }
    }

    public void setBPM(double e)
    {
        this.bpm = e;
        note_speed = ((bpm/240) * measure_size) / 1000.0d;
    }

    /** returns the note speed in pixels/milliseconds */
    public double getNoteSpeed() { return note_speed; }

    public double getBPM() { return bpm; }
    public double getMeasureSize() { return measure_size; }
    public double getViewPort() { return viewport; }


    private int buffer_measure = -1;

    private double fractional_measure = 1;

    private final int buffer_upper_bound = -10;

    /** update the note layer of the entities_matrix.
    *** note buffering is equally distributed between the frames
    **/
    private void update_note_buffer()
    {
        while(buffer_iterator.hasNext() && buffer_offset > buffer_upper_bound)
        {
            Event e = buffer_iterator.next();
            while(e.getMeasure() > buffer_measure) // this is the start of a new measure
            {
                buffer_offset -= measure_size * fractional_measure;
                entities_matrix.get(0).add(
                    new MeasureEntity(this,
                    sprite_map.get("MEASURE_MARK"),
                    screen_x_offset, buffer_offset+6)
                );
                buffer_measure++;
                fractional_measure = 1;
            }

            double abs_height = buffer_offset - (e.getPosition() * measure_size);
            switch(e.getChannel())
            {
                                case TIME_SIGNATURE:
                fractional_measure = e.getValue();
                break;

                case BPM_CHANGE:
                entities_matrix.get(0).add(new BPMEntity(this,e.getValue(),abs_height));
                break;

                case NOTE_1:case NOTE_2:
                case NOTE_3:case NOTE_4:
                case NOTE_5:case NOTE_6:case NOTE_7:
                if(e.getFlag() == Event.Flag.NONE){
                    entities_matrix.get(1).add(
                        new NoteEntity(this, sprite_map.get("HEAD_"+e.getChannel()),
                        channel_x_offset.get(e.getChannel()),
                        abs_height,
                        (int)e.getValue()
                        ));
                }
                else if(e.getFlag() == Event.Flag.HOLD){
                    ln_buffer.put(e.getChannel(),
                        new LongNoteEntity(this,
                        sprite_map.get("HEAD_"+e.getChannel()),
                        sprite_map.get("BODY_"+e.getChannel()),
                        channel_x_offset.get(e.getChannel()),
                        abs_height,
                        (int)e.getValue()
                        ));
                    entities_matrix.get(1).add(ln_buffer.get(e.getChannel()));
                }
                else if(e.getFlag() == Event.Flag.RELEASE){
                    if(ln_buffer.get(e.getChannel()) == null){
                        System.out.println("Attempted to RELEASE note "+e.getChannel());
                    }else{
                        ln_buffer.get(e.getChannel()).setEndY(abs_height);
                        ln_buffer.remove(e.getChannel());
                    }
                }
                break;
                case AUTO_PLAY:
                entities_matrix.get(0).add(new SampleEntity(this,(int)e.getValue(),abs_height));
                break;
            }
        }
    }

    /**
     * Notification that the game window has been closed
     */
    public void windowClosed() {
        SoundManager.killData();
    }

    public void queueSample(int sample_value)
    {
        Integer buffer = samples.get(sample_value);
        if(buffer == null)return;
        Integer source = source_queue.pop();
        SoundManager.play(source, buffer);
        sources_playing.push(source);
    }

    private void check_sources() {
        Iterator<Integer> it = sources_playing.iterator();
        while(it.hasNext())
        {
            Integer i = it.next();
            if(!SoundManager.isPlaying(i)){
                it.remove();
                source_queue.push(i);
            }
        }
    }
}

