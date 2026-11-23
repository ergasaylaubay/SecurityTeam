package kz.securityteam.client;

import javafx.application.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.stage.Stage;
import java.util.concurrent.*;
import java.util.UUID;

public class MessengerApp extends Application {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r); t.setDaemon(true); return t; });
    private ApiClient api;
    private Stage stage;
    private final Label status = new Label();
    private UUID selected;
    private final TextArea history = new TextArea();
    private final ListView<ApiClient.Chat> chats = new ListView<>();
    @Override public void start(Stage stage) throws Exception {
        this.stage = stage;
        api = new ApiClient(System.getProperty("messenger.url", "https://localhost:8443"),
                System.getProperty("messenger.truststore", "secrets/client-trust.p12"),
                System.getenv("MESSENGER_KEYSTORE_PASSWORD"));
        stage.setTitle("Без тим · Қауіпсіз мессенджер"); login(); stage.show();
    }
    interface Job { void run() throws Exception; }
    private void run(Job job) {
        status.setText("Орындалуда…");
        worker.submit(() -> { try { job.run(); Platform.runLater(() -> status.setText("Дайын")); }
            catch (Exception ex) { Platform.runLater(() -> status.setText(error(ex))); } });
    }
    private String error(Exception ex) {
        if (ex instanceof ApiClient.ApiException a) return switch(a.status()) {
            case 401 -> "Логин немесе құпиясөз қате. Қайта кіріңіз.";
            case 403 -> "Бұл әрекетке рұқсат жоқ.";
            case 409 -> "Бұл логин бұрын тіркелген немесе жазба қайталанды.";
            case 400 -> "Деректерді тексеріңіз. Логин: 3–32 латын таңбасы; пароль: кемінде 10 таңба.";
            default -> "Сервер қатесі: " + a.status();
        };
        return "Қосылымды және сертификат баптауын тексеріңіз: " + ex.getClass().getSimpleName();
    }
    private void login() {
        var username = new TextField(); username.setPromptText("Логин");
        var password = new PasswordField(); password.setPromptText("Құпиясөз (кемінде 10 таңба)");
        var enter = new Button("Кіру"); var register = new Button("Тіркелу");
        enter.setOnAction(e -> { String u=username.getText(), p=password.getText(); run(() -> {
            api.login(u,p); Platform.runLater(() -> { password.clear(); chatScreen(); });
        }); });
        register.setOnAction(e -> { String u=username.getText(), p=password.getText(); run(() -> api.register(u,p)); });
        var root = new VBox(14, new Label("Қауіпсіз корпоративтік мессенджер"), username, password,
                new HBox(10, enter, register), status);
        root.setPadding(new Insets(30)); stage.setScene(new Scene(root, 600, 340));
    }
    private void chatScreen() {
        selected = null; history.clear(); history.setEditable(false); history.setWrapText(true);
        var peer = new TextField(); peer.setPromptText("Қатысушының логині");
        var open = new Button("Чат ашу"); var refresh = new Button("Жаңарту"); var logout = new Button("Шығу");
        var text = new TextField(); text.setPromptText("Хабарлама…"); var send = new Button("Жіберу");
        open.setOnAction(e -> { String name=peer.getText(); run(() -> { api.createChat(name); reloadChats(); }); });
        refresh.setOnAction(e -> run(() -> { reloadChats(); refreshHistory(selected); }));
        chats.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            selected = item == null ? null : item.id();
            if (selected != null) { UUID id=selected; run(() -> refreshHistory(id)); }
        });
        send.setOnAction(e -> {
            UUID id=selected; String message=text.getText();
            if (id == null || message.isBlank()) return;
            run(() -> { api.send(id,message); Platform.runLater(text::clear); refreshHistory(id); });
        });
        text.setOnAction(send.getOnAction());
        logout.setOnAction(e -> run(() -> { api.logout(); Platform.runLater(this::login); }));
        var left = new VBox(10, new Label("Жеке чаттар"), chats, peer, open); left.setPrefWidth(210);
        var center = new VBox(10, history, new HBox(10,text,send)); VBox.setVgrow(history, Priority.ALWAYS);
        HBox.setHgrow(text, Priority.ALWAYS);
        var root = new BorderPane(center, new HBox(10,refresh,logout), null, status,left);
        root.setPadding(new Insets(18)); BorderPane.setMargin(center,new Insets(10));
        stage.setScene(new Scene(root,920,600)); run(this::reloadChats);
    }
    private void reloadChats() throws Exception {
        var items=api.chats(); Platform.runLater(() -> {
            UUID previous=selected; chats.getItems().setAll(items);
            if(previous!=null) items.stream().filter(c->c.id().equals(previous)).findFirst().ifPresent(c->chats.getSelectionModel().select(c));
        });
    }
    private void refreshHistory(UUID id) throws Exception {
        if(id==null) return;
        String result=api.history(id);
        Platform.runLater(() -> { if(id.equals(selected)) history.setText(result); });
    }
    @Override public void stop() { worker.shutdownNow(); }
    public static void main(String[] args) { launch(args); }
}
