import java.net.*;
import java.io.*;

public class Server {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java EchoServer <port number>");
            System.exit(1);
        }
         
        int portNumber = Integer.parseInt(args[0]);
         
        try (ServerSocket serverSocket = new ServerSocket(portNumber)) 
        {  
            while(true)
            {
                new Thread(new ServerThread(serverSocket.accept())).start();  
            } 
        }
        
    }
}




class ServerThread implements Runnable {
    
    private Socket clientSocket;
    public ServerThread(Socket client)
    {
        clientSocket = client;
    }
    private double split_calc(String line)
    {
        String[] lines = line.split(" ");
        double a1 = Double.parseDouble(lines[0]);
        char op = lines[1].charAt(0);
        double a2 = Double.parseDouble(lines[2]);
        return calculate(a1, a2, op);
    }
    private double calculate(double arg1, double arg2, char op)
    {  
        switch(op)
        {
            case '+': return arg1+arg2;
            case '-': return arg1-arg2;
            case '*': return arg1*arg2;
            case '/': return arg1/arg2;
        }
        return 0;

    }
    @Override
    public void run() {
        try(
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true); 
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            )
            {
                
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                        out.println(Double.toString(split_calc(inputLine)));   
                }
                clientSocket.close();
            }
         catch (IOException e) {
            System.out.println("Exception caught");
            System.out.println(e.getMessage());
        }
    }
}

