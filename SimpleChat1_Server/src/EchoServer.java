import java.io.*;
import ocsf.server.*;

public class EchoServer extends AbstractServer  //ocsf의 프레임워크 상속
{
    final public static int DEFAULT_PORT = 5555;    //기본 포트 5555

    public EchoServer(int port)
    {
        super(port);
    }

    public void handleMessageFromClient(Object msg, ConnectionToClient client)  //클라이언트로부터 온 메시지 관리
    {
        System.out.println("Message reveived: " + msg + " from " + client); //받은 메시지 및 해당 클라이언트에 대해
        this.sendToAllClients(msg); //모든 클라이언트에게 메시지 전송
    }

    protected void serverStarted()  //서버가 해당 포트로 열려있음을 표시할 때 사용하는 함수
    {   //ocsf의 함수로 AbstractServer의 port변수 값을 반환하는 함수로 추측
        System.out.println("Server listening for connections on port " + getPort());    
    }

    protected void serverStopped()  //해당 포트가 닫혀있음을 표시할 때 사용하는 함수
    {
        System.out.println("Server has stopped listening for connections.");
    }


    public static void main(String[] args)
    {
        int port = 0;

        try{
            port = Integer.parseInt(args[0]);
        } catch (Throwable t) {
            port = DEFAULT_PORT;
        }

        EchoServer sv = new EchoServer(port);

        try{
            sv.listen();    //서버가 클라이언트와 연결 가능하도록(클라이언트의 전송을 기다리도록) 설정하는 함수로 추측(ocsf의 함수)
        } catch (Exception e){
            System.out.println("ERROR - Could not listen for clients!");
        }
    }
}