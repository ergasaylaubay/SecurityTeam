import kz.securityteam.client.ApiClient;
import java.util.UUID;
import java.nio.file.*;

/** Run against a local HTTPS demo server; keeps test account names in work-only state. */
class Smoke {
    public static void main(String[] args) throws Exception {
        String password=System.getenv("MESSENGER_KEYSTORE_PASSWORD");
        var a=new ApiClient("https://localhost:8443","secrets/client-trust.p12",password);
        var b=new ApiClient("https://localhost:8443","secrets/client-trust.p12",password);
        var e=new ApiClient("https://localhost:8443","secrets/client-trust.p12",password);
        Path state=Path.of(args[1]);
        if(args[0].equals("write")) {
            String suffix=UUID.randomUUID().toString().substring(0,8);
            String alice="alice_"+suffix,bob="bob_"+suffix,eve="eve_"+suffix;
            a.register(alice,password); b.register(bob,password); e.register(eve,password);
            a.login(alice,password); b.login(bob,password); e.login(eve,password);
            UUID chat=a.createChat(bob); a.send(chat,"Сәлем!"); b.send(chat,"Жауап!");
            if(!b.history(chat).contains("Сәлем!") || !a.history(chat).contains("Жауап!")) throw new AssertionError("round trip");
            try { e.history(chat); throw new AssertionError("outsider allowed"); }
            catch(ApiClient.ApiException ex) { if(ex.status()!=403) throw ex; }
            Files.writeString(state,alice+"\n"+bob+"\n"+chat);
            System.out.println("HTTPS two-client round trip + outsider denial: PASS");
        } else {
            var lines=Files.readAllLines(state); a.login(lines.get(0),password); b.login(lines.get(1),password);
            UUID chat=UUID.fromString(lines.get(2));
            if(!a.history(chat).contains("Жауап!") || !b.history(chat).contains("Сәлем!")) throw new AssertionError("restart persistence");
            System.out.println("Server restart + fresh clients + retained history: PASS");
        }
    }
}
