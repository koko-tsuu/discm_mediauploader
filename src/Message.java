import java.io.Serializable;
import java.util.Arrays;

enum StatusCode {
    REQUEST,
    QUEUE_FULL,
    FILE_COMPLETE,
    FILE_ALL_COMPLETE;
}

// must implement Serializable in order to be sent
public class Message implements Serializable {
   private final long byteSize;
   private final StatusCode statusCode;
   private final String filename;
   private final int threadIndexProducer;
   private final byte[] bytesToSendArray;

    public Message(long byteSize, StatusCode statusCode, String filename, int threadIndexProducer, byte[] bytesToSendArray) {
        this.byteSize = byteSize;
        this.statusCode = statusCode;
        this.filename = filename;
        this.threadIndexProducer = threadIndexProducer;
        this.bytesToSendArray = bytesToSendArray;

    }

    public Message(StatusCode statusCode) {
        this.statusCode = statusCode;
        this.byteSize = 0;
        this.filename = null;
        this.threadIndexProducer = 0;
        this.bytesToSendArray = null;

    }

    public Message(StatusCode statusCode, String filename, int threadIndexProducer) {
        this.statusCode = statusCode;
        this.byteSize = 0;
        this.filename = null;
        this.threadIndexProducer = threadIndexProducer;
        this.bytesToSendArray = null;

    }


    public Message(StatusCode statusCode, byte[] bytesToSendArray) {
        this.statusCode = statusCode;
        this.byteSize = 0;
        this.filename = null;
        this.threadIndexProducer = -1;
        this.bytesToSendArray = bytesToSendArray;

    }

    StatusCode getStatusCode() {
        return statusCode;
    }
    int getThreadIndexProducer() {
        return threadIndexProducer;
    }

    long getByteSize() {
        return byteSize;
    }
    String getFilename() {
        return filename;
    }

    byte[] getBytesToSendArray() {
        return bytesToSendArray;
    }

    @Override
    public String toString() {
        return byteSize + " " + statusCode + " " + filename + " " + threadIndexProducer;
    }
}
