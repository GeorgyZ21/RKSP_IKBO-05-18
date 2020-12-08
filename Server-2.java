import java.net.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;


//главный поток, который принимает новые подключения
public class Server {

    public static CopyOnWriteArrayList<ClientData> clients = new CopyOnWriteArrayList<ClientData>();

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java EchoServer <port number>");
            System.exit(1);
        }
         
        int portNumber = Integer.parseInt(args[0]);
        final ConcurrentLinkedQueue<ClientTask> tasks = new ConcurrentLinkedQueue<ClientTask>();
        final ConcurrentLinkedQueue<ClientTask> ready = new ConcurrentLinkedQueue<ClientTask>();
        Executor executor = new Executor(tasks, ready, clients);
        new Thread(executor).start();
        TaskAccepter accepter = new TaskAccepter(clients, tasks);
        new Thread(accepter).start();
        ResultSender sender = new ResultSender(clients, ready);
        new Thread(sender).start();
        ServerSocket serverSocket = new ServerSocket(portNumber);
        while(true)
        {
            Socket client = serverSocket.accept();
            System.out.println("connected");
            clients.add(new ClientData(client));
        } 
    
    }
}

class ClientData
{
    public Socket socket;
    public PrintWriter output;
    public BufferedReader input;
    public ClientData(Socket clientSocket)
    {
        socket = clientSocket;
        try{
            output = new PrintWriter(clientSocket.getOutputStream(), true); 
            input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));  
        }
        catch(IOException e)
        {
            try{
             socket.close();
            }
            catch(IOException ee)
            {
                
            }
            finally
            {
              Server.clients.remove(this);  
            }
        }
    }
}

class ClientTask
{
    public String task;
    public ClientData client;
    public ClientTask(String t, ClientData c)
    {
        client = c;
        task = t;  
    }
}

//Поток, который принимает задания от клиентов
class TaskAccepter implements Runnable {
    private CopyOnWriteArrayList<ClientData> clients;
    private ConcurrentLinkedQueue<ClientTask> tasks;
    public TaskAccepter(CopyOnWriteArrayList<ClientData> c, ConcurrentLinkedQueue<ClientTask> t)
    {
        clients = c;
        tasks = t;
    }
    @Override
    public void run() {
        while(true)
        {
            clients.removeIf((client) -> client.socket.isClosed());
            for(ClientData c: clients)
            {
                try
                {
                    String s;
                    if (c.socket.isClosed())
                        throw new IOException();
                    if(c.input.ready())
                    {
                        s = c.input.readLine();
                        if(s.length()==0) // на клиенте пустая строка введена
                        {
                            clients.remove(c);
                        }
                        else
                        {
                            
                            tasks.add(new ClientTask(s, c));
                            System.out.println("got task: "+s);
                        }
                    }
                }
                catch (IOException e)
                {
                    clients.remove(c);
                }
                    
            }
        }
    }
}

//Поток, который принимает результаты от воркера и возвращает клиентам
class ResultSender implements Runnable {
    private CopyOnWriteArrayList<ClientData> clients;
    private ConcurrentLinkedQueue<ClientTask> ready;
    public ResultSender(CopyOnWriteArrayList<ClientData> c, ConcurrentLinkedQueue<ClientTask> r)
    {
        clients = c;
        ready = r;
    }
    @Override
    public void run() {
        while(true)
            if(ready.size() > 0)
            {
                ClientTask t = ready.poll();
                System.out.println("the result: "+t.task);
                t.client.output.println(t.task);
            }
    }
}


//Поток, выполняющий все вычисления
class Executor implements Runnable {
    private ConcurrentLinkedQueue<ClientTask> ready;
    private ConcurrentLinkedQueue<ClientTask> tasks;
    private CopyOnWriteArrayList<ClientData> clients;
    public Executor(ConcurrentLinkedQueue<ClientTask> t, ConcurrentLinkedQueue<ClientTask> r, CopyOnWriteArrayList<ClientData> c)
    {
        ready = r;
        tasks = t;
        clients = c;
    }

    @Override
    public void run() {
        while(true)
        {
            if(tasks.size() > 0)
            {
                ClientTask t = tasks.poll();
                System.out.println("working on: "+t.task);
                int sc = 0;
                String result = Double.toString(calc(t.task));
                t.task = result;
                ready.add(t);
            }
             
        }
                    

    }

    private double calc(String line)
    {
        String[] lines = line.split(" ");
        double arg1 = Double.parseDouble(lines[0]);
        char op = lines[1].charAt(0);
        double arg2 = Double.parseDouble(lines[2]);
        switch(op)
        {
            case '+': return arg1+arg2;
            case '-': return arg1-arg2;
            case '*': return arg1*arg2;
            case '/': return arg1/arg2;
        }
        return 0;

    }

}

