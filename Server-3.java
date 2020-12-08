import java.net.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;


//главный поток, который принимает новые подключения
public class Server {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java EchoServer <port number>");
            System.exit(1);
        }
         
        int portNumber = Integer.parseInt(args[0]);
        final CopyOnWriteArrayList<ClientData> clients = new CopyOnWriteArrayList<ClientData>();
        final ConcurrentLinkedQueue<ClientTask> tasks = new ConcurrentLinkedQueue<ClientTask>();
        final ConcurrentLinkedQueue<ClientTask> ready = new ConcurrentLinkedQueue<ClientTask>();
        Executor worker = new Executor(tasks, ready, clients);
        new Thread(worker).start();
        TaskAccepter listener = new TaskAccepter(clients, tasks);
        new Thread(listener).start();
        ResultSender answerer = new ResultSender(clients, ready);
        new Thread(answerer).start();
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress(portNumber));
        serverSocket.configureBlocking(false);
        while(true)
        {
            final SocketChannel client = serverSocket.accept();
            if (client != null) 
            {
                client.configureBlocking(false);
                System.out.println("connected");
                clients.add(new ClientData(client, ByteBuffer.allocateDirect(128)));
            }
            clients.removeIf((socketChannel) -> !socketChannel.socket.isOpen());
        } 
    
    }
}

class ClientTask
{
    public String task;
    public ClientData client;
    public ClientTask(String t, ClientData c)
    {
        task = t;
        client = c;
    }
}

class ClientData
{
    public ByteBuffer buf;
    public SocketChannel socket;
    public ClientData(SocketChannel clientSocket, ByteBuffer buffer)
    {
        socket = clientSocket;
        buf = buffer;
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

            for(ClientData c: clients)
            {
                try
                {
                    {
                        int numbytes = c.socket.read(c.buf);
                        if (numbytes == -1) 
                            clients.remove(c);
                        else if(numbytes==0);
                        else if(numbytes==2) // на клиенте пустая строка введена
                        {
                            clients.remove(c);
                        }
                        else
                        {
                            c.buf.flip();
                            if(c.buf.remaining()>2)
                            {
                                byte[] b = new byte[c.buf.remaining()];
                                c.buf.get(b);
                                String task = new String(b); //10'n 13'r'
                                tasks.add(new ClientTask(task, c));
                                System.out.println("got task: "+task);
                            }
                        }

                    }
                }
                catch (Exception e)
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
                t.client.buf.rewind();
                t.client.buf.put(t.task.getBytes());
                t.client.buf.rewind();
                try{
                    while (t.client.buf.hasRemaining()) {
                        t.client.socket.write(t.client.buf);
                   }
                }
                catch (IOException e) {
                    clients.remove(t.client);
                }
                t.client.buf.compact();
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

