package org.open2jam.parser;

import java.util.logging.Level;
import org.open2jam.util.OggInputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import org.open2jam.util.Logger;
import org.open2jam.util.ByteBufferInputStream;

import org.open2jam.render.lwjgl.SoundManager;


class OJMParser
{
    /** the xor mask used in the M30 format */
    private static final byte[] nami = new byte[]{0x6E, 0x61, 0x6D, 0x69};


    /** the M30 signature, "M30\0" in little endian */
    private static final int M30_SIGNATURE = 0x0030334D;

    /** the OMC signature, "OMC\0" in little endian */
    private static final int OMC_SIGNATURE = 0x00434D4F;

    /** the OJM signature, "OJM\0" in little endian */
    private static final int OJM_SIGNATURE = 0x004D4A4F;

    /* this is a dump from debugging notetool */
    private static final byte[] REARRANGE_TABLE = new byte[]{
    0x10, 0x0E, 0x02, 0x09, 0x04, 0x00, 0x07, 0x01,
    0x06, 0x08, 0x0F, 0x0A, 0x05, 0x0C, 0x03, 0x0D,
    0x0B, 0x07, 0x02, 0x0A, 0x0B, 0x03, 0x05, 0x0D,
    0x08, 0x04, 0x00, 0x0C, 0x06, 0x0F, 0x0E, 0x10,
    0x01, 0x09, 0x0C, 0x0D, 0x03, 0x00, 0x06, 0x09,
    0x0A, 0x01, 0x07, 0x08, 0x10, 0x02, 0x0B, 0x0E,
    0x04, 0x0F, 0x05, 0x08, 0x03, 0x04, 0x0D, 0x06,
    0x05, 0x0B, 0x10, 0x02, 0x0C, 0x07, 0x09, 0x0A,
    0x0F, 0x0E, 0x00, 0x01, 0x0F, 0x02, 0x0C, 0x0D,
    0x00, 0x04, 0x01, 0x05, 0x07, 0x03, 0x09, 0x10,
    0x06, 0x0B, 0x0A, 0x08, 0x0E, 0x00, 0x04, 0x0B,
    0x10, 0x0F, 0x0D, 0x0C, 0x06, 0x05, 0x07, 0x01,
    0x02, 0x03, 0x08, 0x09, 0x0A, 0x0E, 0x03, 0x10,
    0x08, 0x07, 0x06, 0x09, 0x0E, 0x0D, 0x00, 0x0A,
    0x0B, 0x04, 0x05, 0x0C, 0x02, 0x01, 0x0F, 0x04,
    0x0E, 0x10, 0x0F, 0x05, 0x08, 0x07, 0x0B, 0x00,
    0x01, 0x06, 0x02, 0x0C, 0x09, 0x03, 0x0A, 0x0D,
    0x06, 0x0D, 0x0E, 0x07, 0x10, 0x0A, 0x0B, 0x00,
    0x01, 0x0C, 0x0F, 0x02, 0x03, 0x08, 0x09, 0x04,
    0x05, 0x0A, 0x0C, 0x00, 0x08, 0x09, 0x0D, 0x03,
    0x04, 0x05, 0x10, 0x0E, 0x0F, 0x01, 0x02, 0x0B,
    0x06, 0x07, 0x05, 0x06, 0x0C, 0x04, 0x0D, 0x0F,
    0x07, 0x0E, 0x08, 0x01, 0x09, 0x02, 0x10, 0x0A,
    0x0B, 0x00, 0x03, 0x0B, 0x0F, 0x04, 0x0E, 0x03,
    0x01, 0x00, 0x02, 0x0D, 0x0C, 0x06, 0x07, 0x05,
    0x10, 0x09, 0x08, 0x0A, 0x03, 0x02, 0x01, 0x00,
    0x04, 0x0C, 0x0D, 0x0B, 0x10, 0x05, 0x06, 0x0F,
    0x0E, 0x07, 0x09, 0x0A, 0x08, 0x09, 0x0A, 0x00,
    0x07, 0x08, 0x06, 0x10, 0x03, 0x04, 0x01, 0x02,
    0x05, 0x0B, 0x0E, 0x0F, 0x0D, 0x0C, 0x0A, 0x06,
    0x09, 0x0C, 0x0B, 0x10, 0x07, 0x08, 0x00, 0x0F,
    0x03, 0x01, 0x02, 0x05, 0x0D, 0x0E, 0x04, 0x0D,
    0x00, 0x01, 0x0E, 0x02, 0x03, 0x08, 0x0B, 0x07,
    0x0C, 0x09, 0x05, 0x0A, 0x0F, 0x04, 0x06, 0x10,
    0x01, 0x0E, 0x02, 0x03, 0x0D, 0x0B, 0x07, 0x00,
    0x08, 0x0C, 0x09, 0x06, 0x0F, 0x10, 0x05, 0x0A,
    0x04, 0x00};

    public static HashMap<Integer,Integer> parseFile(File file)
    {
        RandomAccessFile f;
        HashMap<Integer,Integer> ret;
        try{
            f = new RandomAccessFile(file,"r");
            ByteBuffer buffer = f.getChannel().map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, 4);
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int signature = buffer.getInt();

            switch(signature)
            {
                case M30_SIGNATURE:
                    ret = parseM30(f, file);
                    break;

                case OMC_SIGNATURE:           
                    ret = parseOMC(f, true);
                    break;
                case OJM_SIGNATURE:
                    ret = parseOMC(f, false);
                    break;

                default:
                    Logger.global.warning("Unknown OJM signature !!");
                    ret = new HashMap<Integer,Integer>();
            }
            f.close();
        }catch(IOException e) {
            Logger.global.log(Level.WARNING, "IO exception on file {0} : {1}", new Object[]{file.getName(), e.getMessage()});
            ret = new HashMap<Integer,Integer>();
        }
        return ret;
    }

    private static HashMap<Integer,Integer> parseM30(RandomAccessFile f, File file) throws IOException
    {
        ByteBuffer buffer = f.getChannel().map(FileChannel.MapMode.READ_ONLY, 4, 28);
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);

        // header
        int file_format_version = buffer.getInt();
        int encryption_flag = buffer.getInt();
        int sample_count = buffer.getInt();
        int sample_offset = buffer.getInt();
        int payload_size = buffer.getInt();
        int padding = buffer.getInt();

        buffer = f.getChannel().map(FileChannel.MapMode.READ_ONLY, 28, f.getChannel().size()-28);
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);

        HashMap<Integer,Integer> samples = new HashMap<Integer,Integer>();

        for(int i=0; i<sample_count; i++)
        {
            // reached the end of the file before the samples_count
            if(buffer.remaining() < 52){
                Logger.global.log(Level.INFO, "Wrong number of samples on OJM header : {0}", file.getName());
                break;
            }
            byte[] sample_name = new byte[32];
            buffer.get(sample_name);
            int sample_size = buffer.getInt();
            
            short codec_code = buffer.getShort();
            short codec_code2 = buffer.getShort();

            int music_flag = buffer.getInt();
            short ref = buffer.getShort();
            short unk_zero = buffer.getShort();
            int pcm_samples = buffer.getInt();

            byte[] sample_data = new byte[sample_size];
            buffer.get(sample_data);

            if(encryption_flag == 16)nami_xor(sample_data);
            else if(encryption_flag == 0); // let it pass
            else if(encryption_flag < 16)Logger.global.log(Level.WARNING, "Unknown encryption flag({0}) !", encryption_flag);

            int id = SoundManager.newBuffer(
                new OggInputStream(new ByteArrayInputStream(sample_data))
            );
            int value = ref;
            if(codec_code == 0){
                value = 1000 + ref;
            }
            else if(codec_code != 5){
               Logger.global.log(Level.WARNING, "Unknown codec code [{0}] on OJM : {1}", new Object[]{codec_code, file.getName()});
            }
            samples.put(value, id);
        }
        f.close();
        return samples;
    }

    private static void nami_xor(byte[] array)
    {
        for(int i=0;i+3<array.length;i+=4)
        {
            array[i+0] ^= nami[0];
            array[i+1] ^= nami[1];
            array[i+2] ^= nami[2];
            array[i+3] ^= nami[3];
        }
    }

    private static HashMap<Integer, Integer> parseOMC(RandomAccessFile f, boolean decrypt) throws IOException
    {
       HashMap<Integer, Integer> samples =  new HashMap<Integer, Integer>();
       
       ByteBuffer buffer = f.getChannel().map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 4, 16);
       buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);

       short unk1 = buffer.getShort();
       short unk2 = buffer.getShort();
       int wav_start = buffer.getInt();
       int ogg_start = buffer.getInt();
       int filesize = buffer.getInt();

       int file_offset = 20;
       int sample_id = 0; // wav samples use id 0~999

       // reset global variables
       acc_keybyte = 0xFF;
       acc_counter = 0;

       while(file_offset < ogg_start) // WAV data
       {
           buffer = f.getChannel().map(java.nio.channels.FileChannel.MapMode.READ_ONLY, file_offset, 56);
           buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
           file_offset += 56;

           byte[] sample_name = new byte[32];
           buffer.get(sample_name);

           short audio_format = buffer.getShort();
           short num_channels = buffer.getShort();
           int sample_rate = buffer.getInt();
           int bit_rate = buffer.getInt();
           short block_align = buffer.getShort();
           short bits_per_sample = buffer.getShort();
           int data = buffer.getInt();
           int chunk_size = buffer.getInt();

           if(chunk_size == 0){ sample_id++; continue; }

           buffer = f.getChannel().map(java.nio.channels.FileChannel.MapMode.READ_ONLY, file_offset, chunk_size);
           buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
           file_offset += chunk_size;

           byte[] buf = new byte[buffer.remaining()];
           buffer.get(buf);

           if(decrypt)
           {
               buf = rearrange(buf);
               buf = acc_xor(buf);
           }

           buffer = ByteBuffer.allocateDirect(buf.length);
           buffer.put(buf);
           buffer.flip();

           int buffer_id = SoundManager.newBuffer(buffer, bits_per_sample, num_channels, sample_rate);
           samples.put(sample_id, buffer_id);
           sample_id++;
       }
       sample_id = 1000; // ogg samples use id 1000~?
       while(file_offset < filesize) // OGG data
       {
           buffer = f.getChannel().map(java.nio.channels.FileChannel.MapMode.READ_ONLY, file_offset, 36);
           buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
           file_offset += 36;

           byte[] sample_name = new byte[32];
           buffer.get(sample_name);

           int sample_size = buffer.getInt();

           if(sample_size == 0){ sample_id++; continue; }

           buffer = f.getChannel().map(java.nio.channels.FileChannel.MapMode.READ_ONLY, file_offset, sample_size);
           buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
           file_offset += sample_size;

           int buffer_id = SoundManager.newBuffer(new OggInputStream(new ByteBufferInputStream(buffer)));
           samples.put(sample_id, buffer_id);
           sample_id++;
       }

       return samples;
    }

    /**
     * fuck the person who invented this, FUCK YOU!... but with love =$
     */
    private static byte[] rearrange(byte[] buf_encoded)
    {
        int length = buf_encoded.length;
        int key = ((length % 17) << 4) + (length % 17);

        int block_size = length / 17;

        // Let's fill the buffer
        byte[] buf_plain = new byte[length];
        System.arraycopy(buf_encoded, 0, buf_plain, 0, length);

        for(int block=0;block<17;block++) // loopy loop
        {
            int block_start_encoded = block_size * block;	// Where is the start of the enconded block
            int block_start_plain = block_size * REARRANGE_TABLE[key];	// Where the final plain block will be
            System.arraycopy(buf_encoded, block_start_encoded, buf_plain, block_start_plain, block_size);

            key++;
        }
        return buf_plain;
    }

    /** some weird encryption */
    private static int acc_keybyte = 0xFF;
    private static int acc_counter = 0;
    private static byte[] acc_xor(byte[] buf)
    {
        int temp;
        byte this_byte;
        for(int i=0;i<buf.length;i++)
        {
            temp = this_byte = buf[i];

            if(((acc_keybyte << acc_counter) & 0x80)!=0){
                this_byte = (byte) ~this_byte;
            }

            buf[i] = this_byte;
            acc_counter++;
            if(acc_counter > 7){
                acc_counter = 0;
                acc_keybyte = temp;
            }
        }
        return buf;
    }
}
