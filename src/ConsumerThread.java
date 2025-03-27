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

            while (running)
            {
                try {
                    if (filename != null && !buffer.isEmpty())
                    {
                        StatusCode statusCode = buffer.getFirst().getStatusCode();
                        // if request
                        if (statusCode == StatusCode.REQUEST)
                        {
                            System.out.println(Arrays.toString(buffer.getFirst().getBytesToSendArray()));
                            fileOutputStream.write(buffer.getFirst().getBytesToSendArray());
                        }

                        // if end of file
                        else if (statusCode == StatusCode.FILE_COMPLETE)
                        {
                            // file size is sent from producer to check if file is not corrupted
                            if (buffer.getFirst().getByteSize() == currentBytesReceived){
                                System.out.println("Successfully received file: " + filename);
                            }
                            else {
                                System.out.println("Error receiving file: " + filename);
                            }
                            fileOutputStream.close();
                            cleanUpAfterDownloadingFile();
                        }
                        modifyBuffer(1, null);

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }



        }
    }

    private FileOutputStream fileOutputStream;
    private String filename;
    private volatile int currentBytesReceived = 0;
    private volatile boolean running = true;
    private volatile ArrayList<Message> buffer = new ArrayList<>();
    private Thread consumerThread;

    public ConsumerThread() {
        this.consumerThread = new Thread(new CThread());
        this.consumerThread.start();
    }



    void shutdown() {

        this.running = false;
        try {
            consumerThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    void assignNewFile(String filename) {
        try {
            this.filename = filename;
            File file = new File(System.getProperty("user.dir") + "\\output\\" + filename);
            if (file.createNewFile())
            {
                System.out.println("File created: " + filename);
            }
            else {
                System.out.println("File already exists: " + filename);
            }
            
            fileOutputStream = new FileOutputStream(file);
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
        buffer.clear();
        currentBytesReceived = 0;
        filename = null;

    }

    synchronized void modifyBuffer(int type, Message message)
    {
        // add
        if (type == 0)
        {
            buffer.add(message);
        }

        // delete
        else if (type == 1) {
            buffer.removeFirst();
        }

        System.out.println("buffer: " + buffer.size());



    }

}


