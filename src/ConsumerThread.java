import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class ConsumerThread {

    class thread implements Runnable {
        @Override
        public void run() {
            try {
                while (running)
                {
                    if (producerThreadAssigned != -1 && !buffer.isEmpty())
                    {
                        StatusCode statusCode = buffer.getFirst().getStatusCode();
                        // if request
                        if (statusCode == StatusCode.REQUEST)
                        {
                            fileOutputStream.write(buffer.getFirst().getBytesToSendArray());
                        }

                        // if end of file
                        else if (statusCode == StatusCode.FILE_COMPLETE)
                        {
                            // file size is sent from producer to check if file is not corrupted
                            if (buffer.getFirst().getByteSize() == currentBytesReceived){
                                System.out.println("Successfully received file " + filename);
                            }
                            else {
                                System.out.println("Error receiving file " + filename);
                            }
                            fileOutputStream.close();
                        }
                        buffer.removeFirst();

                    }
                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private FileOutputStream fileOutputStream;
    private final int threadIndex;
    private String filename;
    private int currentBytesReceived = 0;
    private int producerThreadAssigned;
    private boolean running = true;
    private ArrayList<Message> buffer = new ArrayList<>();

    public ConsumerThread(int threadIndex) {
        this.threadIndex = threadIndex;
    }



    int getProducerThreadAssigned() {
        return producerThreadAssigned;
    }

    void shutdown() {
        this.running = false;
    }

    void assignNewFile(String filename) {
        try {
            File file = new File(System.getProperty("user.dir") + "\\output\\" + filename);
            file.createNewFile(); // if file already exists will do nothing

            fileOutputStream = new FileOutputStream(file);
            // this.producerThreadAssigned = producerThreadAssigned;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    void cleanUpAfterDownloadingFile()
    {
        producerThreadAssigned = -1;
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

    }
}


