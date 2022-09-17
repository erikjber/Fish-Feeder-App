package st.crosscheck.fishfeeder;

/**
 * All classes that want to received updates when the client receives a new list of FeedTimes
 * must implement this interface and register with the Client.
 *
 * @author Erik Berglund
 */
public interface UpdateListener
{
    void notifyUpdate();
}
