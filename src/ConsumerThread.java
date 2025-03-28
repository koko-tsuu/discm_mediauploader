import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;


public class ConsumerThread {


    enum ModifyBufferType {
        APPEND,
        POP
    }

    class CThread implements Runnable {
        @Override
        public void run() {

            while (running || !buffer.isEmpty())
            {
                try {
                    if (filename != null && !buffer.isEmpty())
                    {
                        StatusCode statusCode = buffer.getFirst().getStatusCode();
                        // if request
                        if (statusCode == StatusCode.REQUEST)
                        {
                            fileOutputStream.write(buffer.getFirst().getBytesToSendArray());
                            currentBytesReceived += buffer.getFirst().getBytesToSendArray().length;
                            modifyBuffer(ModifyBufferType.POP, null);
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
        buffer.clear();
        currentBytesReceived = 0;
        filename = null;

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
            buffer.removeFirst();
        }




    }

}


