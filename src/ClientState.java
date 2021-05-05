public class ClientState {
    String receiver;
    String sender;
    String message;
    int message_id;
    Command lastState;

    public ClientState() {
        lastState = null;
        receiver = null;
        sender = null;
        message = null;
        message_id = -1;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Command getLastState() {
        return lastState;
    }

    public void setLastState(Command lastState) {
        this.lastState = lastState;
    }

    public int getMessage_id() {
        return message_id;
    }

    public void setMessage_id(int message_id) {
        this.message_id = message_id;
    }
}
