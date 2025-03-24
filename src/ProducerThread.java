import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Dictionary;
import java.util.Hashtable;

public class ProducerThread {

    class thread implements Runnable {
        @Override
        public void run()
        {
            try {
                int fileIndex = 0;
                File folder = new File(System.getProperty("user.dir") + "\\" + path);
                File[] listOfFiles = folder.listFiles();

                System.out.println("Current working directory in Java : " + folder);

                if (listOfFiles != null) {
                    for (File file : listOfFiles) {
                        if (file.isFile()) {
                            System.out.println(file.getName());
                        }
                    }
                    if (!checkIfDuplicate(listOfFiles[fileIndex])) {
                        while (fileIndex != listOfFiles.length) {
                            byte[] buffer = new byte[MAX_BYTES];
                            //try-with-resources to ensure closing stream
                            try (FileInputStream fis = new FileInputStream(listOfFiles[fileIndex]);

                                 BufferedInputStream bis = new BufferedInputStream(fis)) {

                                int bytesAmount = 0;
                                while ((bytesAmount = bis.read(buffer)) > 0) {
                                    send(StatusCode.REQUEST, bytesAmount, listOfFiles[fileIndex].getName(), buffer);
                                }

                                send(StatusCode.FILE_COMPLETE, listOfFiles[fileIndex].length(), listOfFiles[fileIndex].getName(), null);
                            }

                            fileIndex++;

                        }
                    }

                    isDone = true;

                } else {
                    System.out.println("No files to upload.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


        }
    }

    private static ObjectOutputStream objectOutputStream;

    private final int threadIndex;
    private boolean isDone = false;
    private final String path;
    private int MAX_BYTES = 1024 * 1024;
    static Dictionary<byte[], Boolean> allVideosHash = new Hashtable<>();

    public ProducerThread(int threadIndex) {

        this.threadIndex = threadIndex;
        this.path = String.valueOf(this.threadIndex);
    }


    static void setObjectStream(ObjectOutputStream objectOutputStream)
    {
        ProducerThread.objectOutputStream = objectOutputStream;

    }

    boolean checkIfDuplicate(File file)
    {
        try {
            InputStream inputStream = new FileInputStream(file);
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            DigestInputStream digestInputStream = new DigestInputStream(inputStream, md);
            byte[] digest = digestInputStream.getMessageDigest().digest();

            if (allVideosHash.get(digest) != null) {
                return true;
            }
            else {
                allVideosHash.put(digest, true);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;

    }

    synchronized void send(StatusCode statusCode, long byteSize, String filename, byte[] data) {
        try {
            if (statusCode == StatusCode.REQUEST) {
                objectOutputStream.writeObject(new Message(byteSize, StatusCode.REQUEST, filename, threadIndex, data));

            }
            else if (statusCode == StatusCode.FILE_COMPLETE) {
                objectOutputStream.writeObject(new Message(StatusCode.FILE_COMPLETE, threadIndex));
            }

            objectOutputStream.flush();
        } catch(Exception e){
            e.printStackTrace();
        }
    }





    boolean getIsDone(){
        return isDone;
    }

}
