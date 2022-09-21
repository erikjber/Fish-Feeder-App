package st.crosscheck.fishfeeder;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import st.crosscheck.fishfeeder.data.FeedingTime;

/**
 * The network client that is responsible for finding and communicating with the
 * server embedded in the feeder.
 *
 * @author Erik Berglund
 */
public class Client
{
    private static final String TAG = Client.class.getSimpleName();
    private static final int PORT = 5050;
    private static final String MULTICAST_ADDRESS = "226.1.1.1";
    private final Context context;
    private String host;
    private Integer port;
    private boolean ready = false;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private WifiManager.MulticastLock lock;
    private final List<FeedingTime> feedingTimes = new ArrayList<>();
    private final List<UpdateListener> updateListeners = new ArrayList<>();

    public Client(Context context, UpdateListener listener)
    {
        this.context = context;
        if(listener != null)
        {
            addUpdateListener(listener);
        }
        new Thread(() -> {
            listenForBeacon();
            updateState();
        }).start();
    }

    public List<FeedingTime> getFeedingTimes()
    {
        return this.feedingTimes;
    }

    public synchronized void doManual(float seconds)
    {
        if(seconds>25.5 || seconds < 0.1)
        {
            throw new IllegalArgumentException("Seconds must be between 0.1 and 25.5, inclusive.");
        }
        Log.d(TAG,"manual");
        sendMessage(new byte[]{'m',(byte)Math.round(seconds*10)});
    }

    public synchronized void updateState()
    {
        Log.d(TAG,"update");
        sendMessageAndUpdateState(new byte[]{'u'});
    }

    public synchronized void deleteFeedingTime(FeedingTime ft)
    {
        Log.d(TAG,"delete");
        sendMessageAndUpdateState(new byte[]{'d',(byte)ft.slot});
    }

    public synchronized void createFeedingTime(FeedingTime ft)
    {
        Log.d(TAG,"create " + ft);
        sendMessageAndUpdateState(new byte[]{'c',(byte)ft.slot,(byte)ft.hour,(byte)ft.minute,ft.getDeciSeconds()});
    }

    private void sendMessage(byte [] bytes)
    {
        try
        {
            setUpConnection();
            transmit(bytes);
            closeConnection();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if(socket != null)
            {
                try
                {
                    socket.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                socket = null;
            }
        }
    }

    private void sendMessageAndUpdateState(byte[] bytes)
    {
        feedingTimes.clear();
        try
        {
            setUpConnection();
            transmit(bytes);
            readState();
            closeConnection();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if(socket != null)
            {
                try
                {
                    socket.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                socket = null;
            }
        }
    }

    private void readState() throws IOException
    {
        int slot = 0;
        while(socket.isConnected())
        {
            int hour = in.read();
            if(hour < 0)
                break;
            int minute = in.read();
            if(minute < 0)
                break;
            int deciSecond = in.read();
            if (deciSecond < 0)
                break;
            FeedingTime ft = new FeedingTime(slot, hour,minute,(float)(deciSecond/10.0),true);
            if(ft.hour < 24 && ft.minute < 60)
            {
                feedingTimes.add(ft);
            }
            slot++;
            Log.d(TAG, "" + ft);
        }
        // Sort the feeding times
        Collections.sort(feedingTimes);

        // Notify listeners that we have an update list
        for (UpdateListener ul:updateListeners)
        {
            ul.notifyUpdate();
        }
    }

    private void listenForBeacon()
    {
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null)
        {
            lock = wifi.createMulticastLock(TAG);
            lock.setReferenceCounted(true);
            lock.acquire();
        }
        // create a broadcast listen socket
        try (MulticastSocket multiSocket = new MulticastSocket(PORT))
        {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            multiSocket.joinGroup(group);
            byte[] multiData = new byte[2048];
            DatagramPacket multiPacket = new DatagramPacket(multiData, multiData.length);
            Log.d(TAG, "Listening for multicast packet on port " + MULTICAST_ADDRESS + ":" + PORT);
            multiSocket.receive(multiPacket);
            String contents = new String(multiPacket.getData(), 0, multiPacket.getLength());
            Log.d(TAG, "Got packet:" + contents);
            Log.d(TAG, "Got packet from " + multiPacket.getAddress().getHostAddress());
            this.host = multiPacket.getAddress().getHostAddress();
            this.port = Integer.parseInt(contents);
            multiSocket.leaveGroup(group);
            ready = true;
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (lock != null)
            {
                lock.release();
            }
        }
    }

    public void close()
    {
        if(socket != null)
        {
            try
            {
                socket.close();
            }
            catch (IOException e)
            {
                Log.e(TAG,"Could not close.", e);
            }
        }
    }

    private void setUpConnection() throws IOException
    {
        if(!ready)
        {
            throw new IOException("The connection is not ready.");
        }
        socket = new Socket(this.host,this.port);
        socket.setTcpNoDelay(true);
        out = socket.getOutputStream();
        in = socket.getInputStream();
    }

    private void closeConnection() throws IOException
    {
        if(socket != null)
        {
            socket.close();
        }
    }

    /**
     * Transmit the message to the server
     */
    private void transmit(byte[] message) throws IOException
    {
        out.write(message);
        out.flush();
    }

    /**
     * Find an unused slot for feeding times.
     * @return the index of the unused slot, -1 if none is found.
     */
    public int getAvailableSlot()
    {
        for(int x = 0;x<18;x++)
        {
            boolean found = false;
            for(FeedingTime ft:feedingTimes)
            {
                if(ft.slot == x)
                {
                    found = true;
                    break;
                }
            }
            if(!found)
            {
                return x;
            }
        }
        return -1;
    }

    public void addUpdateListener(UpdateListener listener)
    {
        updateListeners.add(listener);
    }
}
