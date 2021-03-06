package org.open2jam.gui.parts;

import java.awt.Container;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.logging.Level;
import org.open2jam.util.Logger;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import org.open2jam.parser.Chart;
import org.open2jam.render.TimeRender;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.open2jam.Config;
import org.open2jam.GameOptions;
import org.open2jam.GameOptions.ChannelMod;
import org.open2jam.GameOptions.SpeedType;
import org.open2jam.GameOptions.VisibilityMod;
import org.open2jam.gui.ChartListTableModel;
import org.open2jam.gui.ChartTableModel;
import org.open2jam.gui.ChartModelLoader;
import org.open2jam.parser.ChartList;
import org.open2jam.render.DistanceRender;
import org.open2jam.render.Render;

public class MusicSelection extends javax.swing.JPanel
    implements PropertyChangeListener, ListSelectionListener {

    private class RenderThread extends Thread {

        Container c;
        Render r;
        public RenderThread(Container c, Render r) {
            this.c = c;
            this.r = r;
        }
        @Override
        public void run() {
            c.setEnabled(false);
            r.startRendering();
            c.setEnabled(true);
        }
    }

    private ChartListTableModel model_songlist;
    private ChartTableModel model_chartlist;
    //private File cwd;
    private DisplayMode[] display_modes;
    private int rank = 0;
    private ChartList selected_chart;
    private Chart selected_header;
    private int last_model_idx;
    private final TableRowSorter<ChartListTableModel> table_sorter;
    
    private class FileItem {
        File file;
        
        public FileItem(File f) {
            this.file = f;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + (this.file != null ? this.file.hashCode() : 0);
            return hash;
        }
        
        @Override
        public boolean equals(Object o) {
            return (o instanceof FileItem) && 
                    ((FileItem)o).file.equals(file);
        }
        
        @Override
        public String toString() {
            return file.getName();
        }
    }

    /** Creates new form Interface */
    public MusicSelection() {
        initLogic();
        initComponents();
        
        List<File> list = Config.getDirsList();
        for(File f : list)combo_dirs.addItem(new FileItem(f));
        
        File cwd = Config.getCwd();
        if(cwd == null)
        {
            //just in case, should not go inside this if
            if(!Config.getDirsList().isEmpty())
            {
                cwd =Config.getDirsList().get(0);
                Config.setCwd(cwd);
                loadDir(cwd);
            }
            else
                openFileChooser();
        }
        else
            loadDir(cwd);
        
        load_progress.setVisible(false);
        table_sorter = new TableRowSorter<ChartListTableModel>(model_songlist);
        table_songlist.setRowSorter(table_sorter);
        txt_filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {updateFilter();}
            @Override
            public void removeUpdate(DocumentEvent e) {updateFilter();}
            @Override
            public void changedUpdate(DocumentEvent e) {}
        });

        javax.swing.table.TableColumn col;
        col = table_songlist.getColumnModel().getColumn(0);
        col.setPreferredWidth(180);
        col = table_songlist.getColumnModel().getColumn(1);
        col.setPreferredWidth(30);
        col = table_songlist.getColumnModel().getColumn(2);
        col.setPreferredWidth(80);

        table_chartlist.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e)
            {
                if (e.getValueIsAdjusting()) return;
                javax.swing.ListSelectionModel lsm = (javax.swing.ListSelectionModel)e.getSource();
                if(lsm.isSelectionEmpty()) return;
                int selectedRow = lsm.getMinSelectionIndex();
                if(selectedRow < 0) return;
                if(selected_chart.get(selectedRow) == selected_header)return;
                selected_header = selected_chart.get(selectedRow);

                updateInfo();
            }
        });
        
        readGameOptions();
    }
    
    private void readGameOptions() {
        // TODO: read Config gameOptions and set them on the GUI
        GameOptions go = Config.getGameOptions();
        
        jc_autoplay.setSelected(go.getAutoplay());
        combo_channelModifier.setSelectedItem(go.getChannelModifier());
        combo_visibilityModifier.setSelectedItem(go.getVisibilityModifier());
        slider_main_vol.setValue(Math.round(go.getMasterVolume()*100));
        slider_key_vol.setValue(Math.round(go.getKeyVolume()*100));
        slider_bgm_vol.setValue(Math.round(go.getBGMVolume()*100));
        js_hispeed.setValue(go.getHiSpeed());
        combo_speedType.setSelectedItem(go.getSpeedType());
        
        for(DisplayMode dm : display_modes)
        {
            if(go.isDisplaySaved(dm))
                combo_displays.setSelectedItem(dm);
        }

        jc_full_screen.setSelected(go.getFullScreen());
        jc_bilinear.setSelected(go.getBilinear());
        jc_vsync.setSelected(go.getVsync());
        
    }
    
    /*
     * the parent is telling us the window is closing
     * now is a good time to save the game options
     */
    public void windowClosing() {
        GameOptions go = Config.getGameOptions();

        go.setAutoplay(jc_autoplay.isSelected());
        go.setChannelModifier((ChannelMod)combo_channelModifier.getSelectedItem());
        go.setVisibilityModifier((VisibilityMod)combo_visibilityModifier.getSelectedItem());
        go.setMasterVolume(slider_main_vol.getValue()/100f);
        go.setKeyVolume(slider_key_vol.getValue()/100f);
        go.setBGMVolume(slider_bgm_vol.getValue()/100f);
        go.setHispeed((Double)js_hispeed.getValue());
        go.setSpeedType((SpeedType)combo_speedType.getSelectedItem());
        go.setFullScreen(jc_full_screen.isSelected());
        go.setBilinear(jc_bilinear.isSelected());
        go.setVsync(jc_vsync.isSelected());
        
        go.setDisplay((DisplayMode)combo_displays.getSelectedItem());
        
        Config.setGameOptions(go);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        rank_group = new javax.swing.ButtonGroup();
        panel_list = new javax.swing.JPanel();
        table_scroll = new javax.swing.JScrollPane();
        table_songlist = new javax.swing.JTable();
        txt_filter = new javax.swing.JTextField();
        lbl_search = new javax.swing.JLabel();
        bt_choose_dir = new javax.swing.JButton();
        load_progress = new javax.swing.JProgressBar();
        jLabel2 = new javax.swing.JLabel();
        combo_dirs = new javax.swing.JComboBox();
        btn_reload = new javax.swing.JButton();
        btn_delete = new javax.swing.JButton();
        panel_song = new javax.swing.JPanel();
        panel_modifiers = new javax.swing.JPanel();
        lbl_main_vol = new javax.swing.JLabel();
        slider_main_vol = new javax.swing.JSlider();
        lbl_key_vol = new javax.swing.JLabel();
        slider_key_vol = new javax.swing.JSlider();
        lbl_bgm_vol = new javax.swing.JLabel();
        slider_bgm_vol = new javax.swing.JSlider();
        lbl_channelModifier = new javax.swing.JLabel();
        combo_channelModifier = new javax.swing.JComboBox();
        lbl_visibilityModifier = new javax.swing.JLabel();
        combo_visibilityModifier = new javax.swing.JComboBox();
        js_hispeed = new javax.swing.JSpinner();
        lbl_rank = new javax.swing.JLabel();
        jr_rank_easy = new javax.swing.JRadioButton();
        jr_rank_normal = new javax.swing.JRadioButton();
        jr_rank_hard = new javax.swing.JRadioButton();
        jc_autoplay = new javax.swing.JCheckBox();
        jc_timed_judgment = new javax.swing.JCheckBox();
        combo_speedType = new javax.swing.JComboBox();
        panel_info = new javax.swing.JPanel();
        lbl_level1 = new javax.swing.JLabel();
        lbl_bpm1 = new javax.swing.JLabel();
        lbl_time1 = new javax.swing.JLabel();
        lbl_level = new javax.swing.JLabel();
        lbl_genre1 = new javax.swing.JLabel();
        lbl_filename = new javax.swing.JLabel();
        lbl_genre = new javax.swing.JLabel();
        lbl_artist = new javax.swing.JLabel();
        lbl_title = new javax.swing.JLabel();
        lbl_notes = new javax.swing.JLabel();
        lbl_notes1 = new javax.swing.JLabel();
        lbl_time = new javax.swing.JLabel();
        lbl_keys1 = new javax.swing.JLabel();
        lbl_bpm = new javax.swing.JLabel();
        lbl_keys = new javax.swing.JLabel();
        lbl_cover = new javax.swing.JLabel();
        table_scroll2 = new javax.swing.JScrollPane();
        table_chartlist = new javax.swing.JTable();
        panel_setting = new javax.swing.JPanel();
        combo_displays = new javax.swing.JComboBox();
        txt_res_height = new javax.swing.JTextField();
        txt_res_width = new javax.swing.JTextField();
        jc_vsync = new javax.swing.JCheckBox();
        lbl_display = new javax.swing.JLabel();
        jc_custom_size = new javax.swing.JCheckBox();
        lbl_res_x = new javax.swing.JLabel();
        jc_full_screen = new javax.swing.JCheckBox();
        jc_bilinear = new javax.swing.JCheckBox();
        bt_play = new javax.swing.JButton();

        setPreferredSize(new java.awt.Dimension(900, 673));

        panel_list.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Selection List", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.TOP));

        table_songlist.setAutoCreateRowSorter(true);
        table_songlist.setModel(model_songlist);
        table_songlist.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table_songlist.getSelectionModel().addListSelectionListener(this);
        table_scroll.setViewportView(table_songlist);

        lbl_search.setText("Search:");

        bt_choose_dir.setText("Choose dir");
        bt_choose_dir.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_choose_dirActionPerformed(evt);
            }
        });

        load_progress.setStringPainted(true);

        jLabel2.setText("Saved dirs:");

        combo_dirs.setMaximumSize(new java.awt.Dimension(34, 35));
        combo_dirs.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                combo_dirsItemStateChanged(evt);
            }
        });

        btn_reload.setText("Reload");
        btn_reload.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn_reloadActionPerformed(evt);
            }
        });

        btn_delete.setText("Remove Dir");
        btn_delete.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn_deleteActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout panel_listLayout = new javax.swing.GroupLayout(panel_list);
        panel_list.setLayout(panel_listLayout);
        panel_listLayout.setHorizontalGroup(
            panel_listLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_listLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel_listLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addGroup(panel_listLayout.createSequentialGroup()
                        .addComponent(bt_choose_dir)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(combo_dirs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(load_progress, javax.swing.GroupLayout.PREFERRED_SIZE, 91, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btn_reload, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btn_delete))
                    .addComponent(table_scroll, javax.swing.GroupLayout.DEFAULT_SIZE, 440, Short.MAX_VALUE))
                .addContainerGap())
            .addGroup(javax.swing.GroupLayout.Alignment.CENTER, panel_listLayout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addComponent(lbl_search)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txt_filter, javax.swing.GroupLayout.DEFAULT_SIZE, 399, Short.MAX_VALUE)
                .addGap(10, 10, 10))
        );
        panel_listLayout.setVerticalGroup(
            panel_listLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_listLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel_listLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bt_choose_dir)
                    .addComponent(jLabel2)
                    .addComponent(combo_dirs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btn_reload)
                    .addComponent(btn_delete)
                    .addComponent(load_progress, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(table_scroll, javax.swing.GroupLayout.DEFAULT_SIZE, 508, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(panel_listLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txt_filter, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbl_search))
                .addContainerGap())
        );

        panel_song.setName(""); // NOI18N

        panel_modifiers.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Modifiers", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.TOP));
        panel_modifiers.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        lbl_main_vol.setText("Main Volume:");

        slider_main_vol.setPaintLabels(true);
        slider_main_vol.setToolTipText("Main Volume");
        slider_main_vol.setMaximumSize(new java.awt.Dimension(10, 32767));

        lbl_key_vol.setText("Key Volume:");

        slider_key_vol.setPaintLabels(true);
        slider_key_vol.setToolTipText("Key Volume");
        slider_key_vol.setValue(100);
        slider_key_vol.setMaximumSize(new java.awt.Dimension(10, 32767));

        lbl_bgm_vol.setText("BGM Volume:");

        slider_bgm_vol.setPaintLabels(true);
        slider_bgm_vol.setToolTipText("BGM Volume");
        slider_bgm_vol.setValue(100);

        lbl_channelModifier.setText("Channel Modifier:");

        combo_channelModifier.setModel(new javax.swing.DefaultComboBoxModel(GameOptions.ChannelMod.values()));

        lbl_visibilityModifier.setText("Visibility Modifier:");

        combo_visibilityModifier.setModel(new javax.swing.DefaultComboBoxModel(GameOptions.VisibilityMod.values()));

        js_hispeed.setModel(new javax.swing.SpinnerNumberModel(1.0d, 0.5d, 10.0d, 0.5d));
        js_hispeed.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        lbl_rank.setText("Rank:");

        rank_group.add(jr_rank_easy);
        jr_rank_easy.setSelected(true);
        jr_rank_easy.setText("Easy");
        jr_rank_easy.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jr_rank_easy.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jr_rank_easy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jr_rank_easyActionPerformed(evt);
            }
        });

        rank_group.add(jr_rank_normal);
        jr_rank_normal.setText("Normal");
        jr_rank_normal.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jr_rank_normal.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jr_rank_normal.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jr_rank_normalActionPerformed(evt);
            }
        });

        rank_group.add(jr_rank_hard);
        jr_rank_hard.setText("Hard");
        jr_rank_hard.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jr_rank_hard.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jr_rank_hard.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jr_rank_hardActionPerformed(evt);
            }
        });

        jc_autoplay.setText("Autoplay");

        jc_timed_judgment.setText("Use timed judgment");
        jc_timed_judgment.setToolTipText("Like Bemani games");

        combo_speedType.setModel(new javax.swing.DefaultComboBoxModel(GameOptions.SpeedType.values()));

        javax.swing.GroupLayout panel_modifiersLayout = new javax.swing.GroupLayout(panel_modifiers);
        panel_modifiers.setLayout(panel_modifiersLayout);
        panel_modifiersLayout.setHorizontalGroup(
            panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_modifiersLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panel_modifiersLayout.createSequentialGroup()
                        .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, panel_modifiersLayout.createSequentialGroup()
                                .addComponent(lbl_channelModifier)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(combo_channelModifier, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 41, Short.MAX_VALUE)
                                .addComponent(lbl_visibilityModifier)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(combo_visibilityModifier, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, panel_modifiersLayout.createSequentialGroup()
                                .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(lbl_bgm_vol)
                                    .addComponent(lbl_key_vol)
                                    .addComponent(lbl_main_vol))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(slider_key_vol, javax.swing.GroupLayout.Alignment.TRAILING, 0, 0, Short.MAX_VALUE)
                                    .addComponent(slider_bgm_vol, javax.swing.GroupLayout.Alignment.TRAILING, 0, 0, Short.MAX_VALUE)
                                    .addComponent(slider_main_vol, javax.swing.GroupLayout.DEFAULT_SIZE, 36, Short.MAX_VALUE))
                                .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(panel_modifiersLayout.createSequentialGroup()
                                        .addGap(10, 10, 10)
                                        .addComponent(lbl_rank)
                                        .addGap(27, 27, 27)
                                        .addComponent(jr_rank_easy)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jr_rank_normal)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jr_rank_hard))
                                    .addGroup(panel_modifiersLayout.createSequentialGroup()
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(combo_speedType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(js_hispeed, javax.swing.GroupLayout.PREFERRED_SIZE, 76, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                        .addContainerGap())
                    .addGroup(panel_modifiersLayout.createSequentialGroup()
                        .addComponent(jc_timed_judgment)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 77, Short.MAX_VALUE)
                        .addComponent(jc_autoplay)
                        .addGap(14, 14, 14))))
        );
        panel_modifiersLayout.setVerticalGroup(
            panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_modifiersLayout.createSequentialGroup()
                .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panel_modifiersLayout.createSequentialGroup()
                        .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(lbl_main_vol)
                            .addComponent(slider_main_vol, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lbl_rank))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(lbl_key_vol)
                            .addComponent(slider_key_vol, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(lbl_bgm_vol)
                            .addComponent(slider_bgm_vol, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(js_hispeed, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(combo_speedType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(jr_rank_easy)
                    .addComponent(jr_rank_normal)
                    .addComponent(jr_rank_hard))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(lbl_visibilityModifier)
                        .addComponent(combo_visibilityModifier, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(combo_channelModifier, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(lbl_channelModifier)))
                .addGap(18, 18, 18)
                .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jc_autoplay)
                    .addComponent(jc_timed_judgment)))
        );

        panel_info.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Song Info", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.TOP));

        lbl_level1.setText("Level:");

        lbl_bpm1.setText("BPM:");

        lbl_time1.setText("Time:");

        lbl_level.setFont(lbl_level.getFont());
        lbl_level.setText("content");

        lbl_genre1.setText("Genre:");

        lbl_filename.setFont(lbl_filename.getFont().deriveFont(lbl_filename.getFont().getSize()-1f));
        lbl_filename.setText("filename");

        lbl_genre.setFont(lbl_genre.getFont());
        lbl_genre.setText("content");

        lbl_artist.setFont(lbl_artist.getFont().deriveFont((lbl_artist.getFont().getStyle() | java.awt.Font.ITALIC)));
        lbl_artist.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl_artist.setText("Artist");

        lbl_title.setFont(lbl_title.getFont().deriveFont(lbl_title.getFont().getSize()+7f));
        lbl_title.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl_title.setText("Title");

        lbl_notes.setFont(lbl_notes.getFont());
        lbl_notes.setText("content");

        lbl_notes1.setText("Notes:");

        lbl_time.setFont(lbl_time.getFont());
        lbl_time.setText("content");

        lbl_keys1.setText("Keys:");

        lbl_bpm.setFont(lbl_bpm.getFont());
        lbl_bpm.setText("content");

        lbl_keys.setFont(lbl_keys.getFont());
        lbl_keys.setText("content");

        lbl_cover.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl_cover.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        lbl_cover.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        lbl_cover.setIconTextGap(0);
        lbl_cover.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lbl_coverMouseClicked(evt);
            }
        });

        table_chartlist.setModel(model_chartlist);
        table_chartlist.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table_scroll2.setViewportView(table_chartlist);

        javax.swing.GroupLayout panel_infoLayout = new javax.swing.GroupLayout(panel_info);
        panel_info.setLayout(panel_infoLayout);
        panel_infoLayout.setHorizontalGroup(
            panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 291, Short.MAX_VALUE)
            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(panel_infoLayout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(table_scroll2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 271, Short.MAX_VALUE)
                        .addComponent(lbl_title, javax.swing.GroupLayout.DEFAULT_SIZE, 271, Short.MAX_VALUE)
                        .addGroup(panel_infoLayout.createSequentialGroup()
                            .addComponent(lbl_cover, javax.swing.GroupLayout.PREFERRED_SIZE, 146, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addGroup(panel_infoLayout.createSequentialGroup()
                                    .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(lbl_bpm1)
                                        .addComponent(lbl_genre1)
                                        .addComponent(lbl_level1)
                                        .addComponent(lbl_notes1)
                                        .addComponent(lbl_time1)
                                        .addComponent(lbl_keys1))
                                    .addGap(18, 18, 18)
                                    .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(lbl_level, javax.swing.GroupLayout.DEFAULT_SIZE, 64, Short.MAX_VALUE)
                                        .addComponent(lbl_notes, javax.swing.GroupLayout.DEFAULT_SIZE, 64, Short.MAX_VALUE)
                                        .addComponent(lbl_time, javax.swing.GroupLayout.DEFAULT_SIZE, 64, Short.MAX_VALUE)
                                        .addComponent(lbl_genre, javax.swing.GroupLayout.DEFAULT_SIZE, 64, Short.MAX_VALUE)
                                        .addComponent(lbl_bpm, javax.swing.GroupLayout.DEFAULT_SIZE, 64, Short.MAX_VALUE)
                                        .addComponent(lbl_keys, javax.swing.GroupLayout.DEFAULT_SIZE, 64, Short.MAX_VALUE)))
                                .addComponent(lbl_filename)))
                        .addComponent(lbl_artist, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 271, Short.MAX_VALUE))
                    .addContainerGap()))
        );
        panel_infoLayout.setVerticalGroup(
            panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 399, Short.MAX_VALUE)
            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(panel_infoLayout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(lbl_cover, javax.swing.GroupLayout.PREFERRED_SIZE, 146, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGroup(panel_infoLayout.createSequentialGroup()
                            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(lbl_bpm1)
                                .addComponent(lbl_bpm))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(lbl_genre)
                                .addComponent(lbl_genre1))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(lbl_level)
                                .addComponent(lbl_level1))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(lbl_notes1)
                                .addComponent(lbl_notes))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(lbl_time)
                                .addComponent(lbl_time1))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(lbl_keys1)
                                .addComponent(lbl_keys))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbl_filename)))
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(lbl_title, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(lbl_artist)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(table_scroll2, javax.swing.GroupLayout.DEFAULT_SIZE, 155, Short.MAX_VALUE)
                    .addContainerGap()))
        );

        javax.swing.GroupLayout panel_songLayout = new javax.swing.GroupLayout(panel_song);
        panel_song.setLayout(panel_songLayout);
        panel_songLayout.setHorizontalGroup(
            panel_songLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_songLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel_songLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(panel_info, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(panel_modifiers, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        panel_songLayout.setVerticalGroup(
            panel_songLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panel_songLayout.createSequentialGroup()
                .addComponent(panel_info, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(panel_modifiers, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        combo_displays.setModel(new javax.swing.DefaultComboBoxModel(display_modes));

        txt_res_height.setText("600");
        txt_res_height.setEnabled(false);

        txt_res_width.setText("800");
        txt_res_width.setEnabled(false);

        jc_vsync.setSelected(true);
        jc_vsync.setText("Use VSync");

        lbl_display.setText("Display:");

        jc_custom_size.setFont(new java.awt.Font("Tahoma", 0, 10));
        jc_custom_size.setText("Custom size:");
        jc_custom_size.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                jc_custom_size_clicked(evt);
            }
        });

        lbl_res_x.setText("x");

        jc_full_screen.setText("Full screen");

        jc_bilinear.setSelected(true);
        jc_bilinear.setText("Bilinear filter");

        javax.swing.GroupLayout panel_settingLayout = new javax.swing.GroupLayout(panel_setting);
        panel_setting.setLayout(panel_settingLayout);
        panel_settingLayout.setHorizontalGroup(
            panel_settingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_settingLayout.createSequentialGroup()
                .addGroup(panel_settingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panel_settingLayout.createSequentialGroup()
                        .addComponent(jc_vsync)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jc_full_screen)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jc_bilinear))
                    .addGroup(panel_settingLayout.createSequentialGroup()
                        .addComponent(lbl_display)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(combo_displays, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jc_custom_size, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txt_res_width, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lbl_res_x)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txt_res_height, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        panel_settingLayout.setVerticalGroup(
            panel_settingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_settingLayout.createSequentialGroup()
                .addGroup(panel_settingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(txt_res_height, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbl_res_x)
                    .addComponent(txt_res_width, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jc_custom_size)
                    .addComponent(combo_displays, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbl_display))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(panel_settingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jc_vsync)
                    .addComponent(jc_full_screen)
                    .addComponent(jc_bilinear))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        bt_play.setFont(new java.awt.Font("Tahoma", 1, 24));
        bt_play.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/open2jam_icon.png"))); // NOI18N
        bt_play.setText("PLAY !!!");
        bt_play.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_playActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(panel_song, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bt_play, javax.swing.GroupLayout.PREFERRED_SIZE, 359, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(panel_setting, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap())
                    .addComponent(panel_list, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(panel_song, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(panel_list, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(panel_setting, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(bt_play)
                        .addGap(11, 11, 11))))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void bt_choose_dirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_choose_dirActionPerformed
       openFileChooser();
}//GEN-LAST:event_bt_choose_dirActionPerformed

    private void btn_reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn_reloadActionPerformed
        updateSelection(Config.getCwd());
}//GEN-LAST:event_btn_reloadActionPerformed

    private void btn_deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn_deleteActionPerformed
        String str = "Are you sure?";
        if(JOptionPane.showConfirmDialog(this, str, "Warning",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION) return;       
        
        ArrayList<File> dir_list = Config.getDirsList();

        File rem = Config.getCwd();
        Config.delCache(rem);
        if(dir_list.contains(rem))
        {
            dir_list.remove(rem);
            combo_dirs.removeItem(new FileItem(rem));
            Config.setDirsList(dir_list);
        }
        
        if(dir_list.isEmpty())
            openFileChooser();   
        else
        {
            File f = dir_list.get(0);
            loadDir(f);
        }
}//GEN-LAST:event_btn_deleteActionPerformed

    private void lbl_coverMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lbl_coverMouseClicked
        if(selected_header == null)return;
        BufferedImage i = selected_header.getCover();
        if(i == null) return;
        JOptionPane.showMessageDialog(this, null, "Cover",
                JOptionPane.INFORMATION_MESSAGE, new ImageIcon(i));
}//GEN-LAST:event_lbl_coverMouseClicked

    private void jc_custom_size_clicked(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_jc_custom_size_clicked
        if (evt.getStateChange() == ItemEvent.SELECTED){
            combo_displays.setEnabled(false);
            txt_res_width.setEnabled(true);
            lbl_res_x.setEnabled(true);
            txt_res_height.setEnabled(true);
        }else{
            txt_res_width.setEnabled(false);
            lbl_res_x.setEnabled(false);
            txt_res_height.setEnabled(false);
            combo_displays.setEnabled(true);
        }
}//GEN-LAST:event_jc_custom_size_clicked

    private void bt_playActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_playActionPerformed
        if(selected_header == null) return;
        
        
        final double hispeed = (Double) js_hispeed.getValue();

        final DisplayMode dm;
        if(jc_custom_size.isSelected()){ // custom size selected
            int w,h;
            try{
                w = Integer.parseInt(txt_res_width.getText());
                h = Integer.parseInt(txt_res_height.getText());
            }catch(Exception e){
                JOptionPane.showMessageDialog(this, "Invalid value on custom size", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            dm = new DisplayMode(w,h);
        }else{
            dm = (DisplayMode) combo_displays.getSelectedItem();
        }
        
        final boolean vsync = jc_vsync.isSelected();
        boolean fs = jc_full_screen.isSelected();

        final boolean autoplay = jc_autoplay.isSelected();

        final boolean time_judgment = jc_timed_judgment.isSelected();

        final SpeedType speed_type =(SpeedType) combo_speedType.getSelectedItem();

        final ChannelMod channelModifier = (ChannelMod) combo_channelModifier.getSelectedItem();
        final VisibilityMod visibilityModifier = (VisibilityMod) combo_visibilityModifier.getSelectedItem();

        final float mainVol = slider_main_vol.getValue() / 100f;
        final float keyVol = slider_key_vol.getValue() / 100f;
        final float bgmVol = slider_bgm_vol.getValue() / 100f;

        final boolean bilinear = jc_bilinear.isSelected();

        if(!dm.isFullscreenCapable() && fs) {
            String str = "This monitor can't support the selected resolution.\n"
                    + "Do you want to play it in windowed mode?";
            if(JOptionPane.showConfirmDialog(this, str, "Warning",
                    JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE)
                    == JOptionPane.YES_OPTION)
                fs = false;
        }

        GameOptions go = Config.getGameOptions();
        go.setAutoplay(autoplay);
        go.setChannelModifier(channelModifier);
        go.setVisibilityModifier(visibilityModifier);
        go.setMasterVolume(mainVol);
        go.setKeyVolume(keyVol);
        go.setBGMVolume(bgmVol);
        go.setHispeed(hispeed);
        go.setSpeedType(speed_type);
        go.setFullScreen(fs);
        go.setBilinear(bilinear);
        go.setVsync(vsync);

        final Render r;
        if(time_judgment)
            r = new TimeRender(selected_header, go, dm);
        else
            r = new DistanceRender(selected_header, go, dm);

        new RenderThread(this.getTopLevelAncestor(), r).start();
}//GEN-LAST:event_bt_playActionPerformed

    private void jr_rank_easyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jr_rank_easyActionPerformed
        rank = 0;
        int sel_row = table_songlist.getSelectedRow();
        if(sel_row >= 0)last_model_idx = table_songlist.convertRowIndexToModel(sel_row);
        model_songlist.setRank(rank);
}//GEN-LAST:event_jr_rank_easyActionPerformed

    private void jr_rank_normalActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jr_rank_normalActionPerformed
        rank = 1;
        int sel_row = table_songlist.getSelectedRow();
        if(sel_row >= 0)last_model_idx = table_songlist.convertRowIndexToModel(sel_row);
        model_songlist.setRank(rank);
}//GEN-LAST:event_jr_rank_normalActionPerformed

    private void jr_rank_hardActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jr_rank_hardActionPerformed
        rank = 2;
        int sel_row = table_songlist.getSelectedRow();
        if(sel_row >= 0)last_model_idx = table_songlist.convertRowIndexToModel(sel_row);
        model_songlist.setRank(rank);
}//GEN-LAST:event_jr_rank_hardActionPerformed

    private void combo_dirsItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_combo_dirsItemStateChanged
        if(combo_dirs.getSelectedIndex() == -1) return;
        File dir = ((FileItem)combo_dirs.getSelectedItem()).file;
        if(dir.equals(Config.getCwd())) return;
        loadDir(dir);
    }//GEN-LAST:event_combo_dirsItemStateChanged

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bt_choose_dir;
    private javax.swing.JButton bt_play;
    private javax.swing.JButton btn_delete;
    private javax.swing.JButton btn_reload;
    private javax.swing.JComboBox combo_channelModifier;
    private javax.swing.JComboBox combo_dirs;
    private javax.swing.JComboBox combo_displays;
    private javax.swing.JComboBox combo_speedType;
    private javax.swing.JComboBox combo_visibilityModifier;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JCheckBox jc_autoplay;
    private javax.swing.JCheckBox jc_bilinear;
    private javax.swing.JCheckBox jc_custom_size;
    private javax.swing.JCheckBox jc_full_screen;
    private javax.swing.JCheckBox jc_timed_judgment;
    private javax.swing.JCheckBox jc_vsync;
    private javax.swing.JRadioButton jr_rank_easy;
    private javax.swing.JRadioButton jr_rank_hard;
    private javax.swing.JRadioButton jr_rank_normal;
    private javax.swing.JSpinner js_hispeed;
    private javax.swing.JLabel lbl_artist;
    private javax.swing.JLabel lbl_bgm_vol;
    private javax.swing.JLabel lbl_bpm;
    private javax.swing.JLabel lbl_bpm1;
    private javax.swing.JLabel lbl_channelModifier;
    private javax.swing.JLabel lbl_cover;
    private javax.swing.JLabel lbl_display;
    private javax.swing.JLabel lbl_filename;
    private javax.swing.JLabel lbl_genre;
    private javax.swing.JLabel lbl_genre1;
    private javax.swing.JLabel lbl_key_vol;
    private javax.swing.JLabel lbl_keys;
    private javax.swing.JLabel lbl_keys1;
    private javax.swing.JLabel lbl_level;
    private javax.swing.JLabel lbl_level1;
    private javax.swing.JLabel lbl_main_vol;
    private javax.swing.JLabel lbl_notes;
    private javax.swing.JLabel lbl_notes1;
    private javax.swing.JLabel lbl_rank;
    private javax.swing.JLabel lbl_res_x;
    private javax.swing.JLabel lbl_search;
    private javax.swing.JLabel lbl_time;
    private javax.swing.JLabel lbl_time1;
    private javax.swing.JLabel lbl_title;
    private javax.swing.JLabel lbl_visibilityModifier;
    private javax.swing.JProgressBar load_progress;
    private javax.swing.JPanel panel_info;
    private javax.swing.JPanel panel_list;
    private javax.swing.JPanel panel_modifiers;
    private javax.swing.JPanel panel_setting;
    private javax.swing.JPanel panel_song;
    private javax.swing.ButtonGroup rank_group;
    private javax.swing.JSlider slider_bgm_vol;
    private javax.swing.JSlider slider_key_vol;
    private javax.swing.JSlider slider_main_vol;
    private javax.swing.JTable table_chartlist;
    private javax.swing.JScrollPane table_scroll;
    private javax.swing.JScrollPane table_scroll2;
    private javax.swing.JTable table_songlist;
    private javax.swing.JTextField txt_filter;
    private javax.swing.JTextField txt_res_height;
    private javax.swing.JTextField txt_res_width;
    // End of variables declaration//GEN-END:variables
    private void initLogic() {

        try {
            List<DisplayMode> list = Arrays.asList(Display.getAvailableDisplayModes());

            Collections.sort(list, new Comparator<DisplayMode>() {
                @Override
                public int compare(DisplayMode dm1, DisplayMode dm2) {

                    if(dm1.getBitsPerPixel() == dm2.getBitsPerPixel())
                    {
                        if(dm1.getWidth() == dm2.getWidth())
                        {
                            if(dm1.getHeight() == dm2.getHeight())
                            {
                                if(dm1.getFrequency() > dm2.getFrequency())return -1;
                                else if(dm1.getFrequency() < dm2.getFrequency())return 1;
                                else return 0;
                            }
                            else if(dm1.getHeight() > dm2.getHeight())return -1;
                            else return 1;
                        }
                        else if(dm1.getWidth() > dm2.getWidth())return -1;
                        else return 1;
                    }
                    else if(dm1.getBitsPerPixel() > dm2.getBitsPerPixel()) return -1;
                    return 1;
                }
            });
            display_modes = list.toArray(new DisplayMode[list.size()]);

        } catch (LWJGLException ex) {
            Logger.global.log(Level.WARNING, "Could not get the display modes !! {0}", ex.getMessage());
            display_modes = new DisplayMode[0];
        }

        model_songlist = new ChartListTableModel();
        model_chartlist = new ChartTableModel();
    }

    private void loadDir(File dir)
    {
        Config.setCwd(dir);
        if(dir == null) return;
        
        ArrayList<ChartList> l = Config.getCache(dir);
        
        if(l == null) {
            updateSelection(dir);
        } else {
            model_songlist.setRawList(l);
        }
        
        ArrayList<File> dir_list = Config.getDirsList();
        if(!dir_list.contains(dir))
        {
            dir_list.add(dir);
            combo_dirs.addItem(new FileItem(dir));
            Config.setDirsList(dir_list);
        }   
        // update combo box dir list
        //System.out.println("set "+dir);
        combo_dirs.setSelectedItem(new FileItem(dir));
    }
    
    private void openFileChooser()
    {
        if(Config.getDirsList().isEmpty())
            Config.setCwd(null);
        
        JFileChooser jfc = new JFileChooser();
        jfc.setCurrentDirectory(Config.getCwd());
        jfc.setDialogTitle("Choose a directory");
        jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        jfc.setAcceptAllFileFilterUsed(false);
        if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadDir(jfc.getSelectedFile());
        }
        else
        {
//            JOptionPane.showMessageDialog(this, "You haven't selected any directory. Byebye...", "You failed to humanity :(", JOptionPane.ERROR_MESSAGE);
//            System.exit(1);
        }
    }

    private void updateSelection(File f) {
        bt_choose_dir.setEnabled(false);
        btn_reload.setVisible(false);
        btn_delete.setVisible(false);
        combo_dirs.setEnabled(false);
        txt_filter.setEnabled(false);
        table_songlist.setEnabled(false);
        load_progress.setValue(0);
        load_progress.setVisible(true);
        ChartModelLoader task = new ChartModelLoader(model_songlist, f);
        task.addPropertyChangeListener(this);
        task.execute();
    }
    
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if("progress".equals(evt.getPropertyName()))
        {
            int i = (Integer) evt.getNewValue();
            load_progress.setValue(i);
            if(i == 100)
            {
                bt_choose_dir.setEnabled(true);
                btn_delete.setVisible(true);
                combo_dirs.setEnabled(true);
                btn_reload.setVisible(true);
                load_progress.setVisible(false);
                txt_filter.setEnabled(true);
                table_songlist.setEnabled(true);
            }
        }
    }

    void updateFilter() {
        try {
            if(txt_filter.getText().length() == 0)table_sorter.setRowFilter(null);
            else table_sorter.setRowFilter(RowFilter.regexFilter("(?i)"+txt_filter.getText()));
        } catch (java.util.regex.PatternSyntaxException ignored) {
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        int i = table_songlist.getSelectedRow();
        if(i < 0 && last_model_idx >= 0){
            i = last_model_idx;
            int i_view = table_songlist.convertRowIndexToView(i);
            table_songlist.getSelectionModel().setSelectionInterval(0, i_view);
            table_scroll.getVerticalScrollBar().setValue(table_songlist.getCellRect(i_view, 0, false).y);
        }else{
            i = table_songlist.convertRowIndexToModel(i);
        }
        if(model_songlist.getRow(i) == selected_chart)return;
        selected_chart = model_songlist.getRow(i);
        if(selected_chart.size() > rank)selected_header = selected_chart.get(rank);
        if(selected_chart != model_chartlist.getChartList()){
            model_chartlist.clear();
            model_chartlist.setChartList(selected_chart);
        }
        if(selected_chart.size()-1 < rank)
            table_chartlist.getSelectionModel().setSelectionInterval(0, 0);
        else
            table_chartlist.getSelectionModel().setSelectionInterval(0, rank);
        
        updateInfo();
    }

    private DecimalFormat bpm_format = new DecimalFormat(".##");
    private void updateInfo()
    {
        if(selected_header == null)return;
        if(!selected_header.getSource().exists()) {JOptionPane.showMessageDialog(this, "Doesn't Exist"); return;}
        lbl_artist.setText(resizeString(selected_header.getArtist(), 40));
        lbl_title.setText(resizeString(selected_header.getTitle(), 30));
        lbl_filename.setText(resizeString(selected_header.getSource().getName(), 30));
        lbl_genre.setText(resizeString(selected_header.getGenre(), 30));
        lbl_level.setText(selected_header.getLevel()+"");
        lbl_bpm.setText(bpm_format.format(selected_header.getBPM()));
        lbl_notes.setText(selected_header.getNoteCount()+"");
	lbl_keys.setText(selected_header.getKeys()+"");
        lbl_time.setText(time2Text(selected_header.getDuration()));

        BufferedImage i = selected_header.getCover();

        if(i != null)
        lbl_cover.setIcon(new ImageIcon(i.getScaledInstance(
                lbl_cover.getWidth(),
                lbl_cover.getHeight(),
                BufferedImage.SCALE_SMOOTH
                )));
        else
            lbl_cover.setIcon(null);
    }
    
    private String time2Text(int secs) {
        int h = secs / 60;
        int m = secs % 60;
        return m < 10 ? h+":0"+m : h+":"+m;
    }

    private String resizeString(String string, int size)
    {
        if(string == null)return "";
        if(string.length() > size)
            string = string.substring(0, size)+"...";
        return string;
    }

}
