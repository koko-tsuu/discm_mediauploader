import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;


public class ConsumerThread {

    class CThread implements Runnable {
        @Override
        public void run() {

            // Ensuring that buffer is empty before terminating
            while (running || !buffer.isEmpty())
            {
                try {

                    // NOTE: This implementation is reliant on the filename not being empty and is unique

                    if (filename != null && !buffer.isEmpty())
                    {
                        StatusCode statusCode = buffer.get(0).getStatusCode();

                        // if request
                        if (statusCode == StatusCode.REQUEST)
                        {
                            fileOutputStream.write(buffer.get(0).getBytesToSendArray());
                            currentBytesReceived += buffer.get(0).getBytesToSendArray().length;
                            modifyBuffer(ModifyBufferType.POP, null);
                        }

                        // if end of file
                        else if (statusCode == StatusCode.FILE_COMPLETE)
                        {
                            // file size is sent from producer to check if file is not corrupted
                            if (buffer.get(0).getByteSize() == currentBytesReceived){
                                System.out.println("[File Downloaded] Successfully received file: " + filename + ". Compressing");

                                FFmpeg ffmpeg = new FFmpeg(System.getProperty("user.dir") + "\\ffmpeg\\bin\\ffmpeg");
                                FFprobe ffprobe = new FFprobe(System.getProperty("user.dir") + "\\ffmpeg\\bin\\ffprobe");

// Input File
                                String inputFile = System.getProperty("user.dir") + "\\output\\" + filename;
                                String outputFile = System.getProperty("user.dir") + "\\output\\compressed\\" + filename;

// Probe Video
                                FFmpegProbeResult probeResult = ffprobe.probe(inputFile);

                                FFmpegBuilder builder = new FFmpegBuilder()
                                        .setInput(probeResult)    // Input file
                                        .overrideOutputFiles(true) // Override existing files
                                        .addOutput(outputFile)  // Output file
                                        .setFormat("mp4")  // Output format
                                        .setVideoCodec("libx264") // Codec
                                        .setVideoBitRate(1_000_000) // Set a lower video bitrate (1Mbps)
                                        .setAudioCodec("aac") // Audio codec
                                        .setAudioBitRate(128_000) // Reduce audio bitrate to 128kbps
                                        .setVideoFrameRate(30, 1) // Ensure a proper frame rate
                                        .addExtraArgs("-preset", "slow")  // Slower preset = better compression
                                        .addExtraArgs("-crf", "28")  // Lower CRF = better quality, higher = better compression
                                        .done();  // Finalizes the output

                                FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);

// Run FFmpeg
                                executor.createJob(builder).run();
                                System.out.println("[Compression] File finished compressing: " + filename);

                            }
                            else {
                                System.out.println("[Error] Error receiving file: " + filename);
                            }
                            fileOutputStream.close();
                            cleanUpAfterDownloadingFile();
                        }


                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }



        }
    }

    private FileOutputStream fileOutputStream;
    private String filename;
    private int currentBytesReceived = 0;                                   // file length check to see if file was successfully downloaded
    private volatile boolean running = true;                                // to gracefully shutdown the thread
    private volatile ArrayList<Message> buffer = new ArrayList<>();         // store all data for a related file
    private Thread consumerThread;
    static MainWindow mainWindow;

    public ConsumerThread() {
        this.consumerThread = new Thread(new CThread());
        this.consumerThread.start();
    }

    static void setMainWindow(MainWindow mainWindow) {
        ConsumerThread.mainWindow = mainWindow;
    }

    void shutdown() {

        this.running = false;
        try {
            consumerThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // Reassign the fileOutputStream to make it point to the new file
    void assignNewFile(String filename) {
        try {

            this.filename = filename;
            File file = new File(System.getProperty("user.dir") + "\\output\\" + filename);

            if (file.createNewFile())
            {
                System.out.println("File created: " + filename);
            }
            else {
                System.out.println("File already exists, overwriting: " + filename);
            }
            
            fileOutputStream = new FileOutputStream(file, false);

            // this.producerThreadAssigned = producerThreadAssigned;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    String getFileName() {
        return filename;
    }

    void cleanUpAfterDownloadingFile()
    {
        mainWindow.addDownloadedVideoUI(filename);
        buffer.clear();
        currentBytesReceived = 0;
        filename = null;
    }

    enum ModifyBufferType {
        APPEND,
        POP
    }

    synchronized void modifyBuffer(ModifyBufferType type, Message message)
    {
        // add
        if (type == ModifyBufferType.APPEND)
        {
            buffer.add(message);
        }

        // delete
        else if (type == ModifyBufferType.POP) {
            buffer.remove(0);
        }




    }

}


