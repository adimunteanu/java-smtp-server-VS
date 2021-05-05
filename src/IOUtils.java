import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class IOUtils {
    private static final String BASE_PATH = System.getProperty("user.dir");
    private static final String EMAILS_PATH = "/emails";

    private static boolean initRoot(){
        File root = new File(BASE_PATH + EMAILS_PATH);

        if(!root.exists()){
            return root.mkdir();
        }

        return true;
    }

    private static boolean initReceiver(String email) {

        File root = new File(BASE_PATH + EMAILS_PATH);

        if(!initRoot()){
            try {
                throw new Exception("Root folder does not exist. Please initialize the root folder first.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        File receiver = new File(getPathFromReceiver(email));

        if(!receiver.exists()){
            return receiver.mkdir();
        }

        return true;
    }

    public static boolean createEmailFile(String receiver, String sender, int messageId, String messageData){
        try {
            if(initReceiver(receiver)){
                File email = new File(getPathFromReceiver(receiver) + "/" + sender + "_" + String.valueOf(messageId) + ".txt");
                if(email.createNewFile()){
                    BufferedWriter writer = new BufferedWriter(new FileWriter(email));
                    writer.write(messageData);
                    writer.close();
                    return true;
                }else{
                    throw new Exception("Email file with this name already exists.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public static boolean createEmailFile(ClientState state){
        try {
            createEmailFile(state.receiver, state.sender, state.message_id, state.message);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private static String getPathFromReceiver(String email){
        return BASE_PATH + EMAILS_PATH + "/" + email;
    }
}
