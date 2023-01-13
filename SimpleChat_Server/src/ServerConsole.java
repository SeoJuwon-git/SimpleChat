import java.io.*;
import ocsf.server.*;   //아직 사용되진 않는듯
import common.*;
import server.*;

public class ServerConsole implements ChatIF {
    final public static int DEFAULT_PORT = 5555;

    EchoServer server;  //서버 콘솔은 에코 서버를 가지고 있음

    public ServerConsole(int port) throws IOException { //서버도 콘솔 입력이 가능해지도록 함에 따라 에코서버와 콘솔 사이의 문맥이 교환되고 콘솔 생성자에서 서버 객체가 같이 생성되도록 됨.
        server = new EchoServer(port, this);
    }

    public void display(String message) {
        System.out.println(message);
    }

    public void accept() {  
        try {
            BufferedReader fromConsole = new BufferedReader(new InputStreamReader(System.in));
            String message;

            try {
                while (true) {  //서버도 콘솔의 입력을 받기 위해 무한 반복
                    message = fromConsole.readLine();
                    server.handleMessageFromServerUI(message);
                }
            } catch (NullPointerException e) { }
        } catch (Exception ex) {
            display("Unexpected error while reading form consle!");
        }
    }

    public static void main(String[] args) {
        int port = 0;

        try {   //포트 번호를 파라미터로 받거나 기본 포트 사용
            port = Integer.parseInt(args[0]);
        } catch (Throwable t) {
            port = DEFAULT_PORT;
        }

        try {
            ServerConsole sv = new ServerConsole(port); //서버 콘솔 객체 생성 및 입력 대기
            sv.accept();
        } catch (IOException e) {
            System.out.println("Could not start listening for clients.");
        }
    }
}
