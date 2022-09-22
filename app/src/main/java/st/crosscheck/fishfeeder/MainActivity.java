package st.crosscheck.fishfeeder;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TimePicker;

import com.google.android.material.snackbar.Snackbar;

import java.util.Calendar;
import java.util.GregorianCalendar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import st.crosscheck.fishfeeder.data.FeedingTime;
import st.crosscheck.fishfeeder.databinding.ActivityMainBinding;
import st.crosscheck.fishfeeder.list.FeedingTimeAdapter;
import st.crosscheck.fishfeeder.list.SwipeToDeleteCallback;

public class MainActivity extends AppCompatActivity implements UpdateListener
{
    private static final String TAG = MainActivity.class.getSimpleName();
    // The number of seconds to run the servo when the "MANUAL" button is pressed.
    private static final float MANUAL_FEEDING_SECONDS = 0.3f;

    private Client client;
    private RecyclerView recyclerView;
    private CoordinatorLayout coordinatorLayout;
    private FeedingTimeAdapter mAdapter;
    private View progressBarHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        st.crosscheck.fishfeeder.databinding.ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        progressBarHolder = binding.getRoot().findViewById(R.id.progressBarHolder);

        recyclerView = binding.getRoot().findViewById(R.id.store_listview);
        coordinatorLayout = binding.getRoot().findViewById(R.id.coordinatorLayout);

        Button manualButton = binding.getRoot().findViewById(R.id.manualButton);
        manualButton.setOnClickListener(this::sendManual);

        Button addButton = binding.getRoot().findViewById(R.id.addButton);
        addButton.setOnClickListener(this::createFeedingTime);

    }

    @Override
    public void onResume()
    {
        super.onResume();
        progressBarHolder.setVisibility(View.VISIBLE);
        client = new Client(this, this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        client.close();
    }

    private void enableSwipeToDeleteAndUndo() {
        SwipeToDeleteCallback swipeToDeleteCallback = new SwipeToDeleteCallback(this) {
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int i) {

                final int position = viewHolder.getAdapterPosition();
                final FeedingTime item = mAdapter.getFeedingTime(position);
                if(item != null)
                {
                    deleteFeedingTime(item);

                    Snackbar snackbar = Snackbar
                            .make(coordinatorLayout, getText(R.string.item_was_removed), Snackbar.LENGTH_LONG);
                    snackbar.setAction(getText(R.string.undo), view -> createFeedingTime(item));

                    snackbar.setActionTextColor(Color.YELLOW);
                    snackbar.show();
                }

            }
        };

        ItemTouchHelper itemTouchhelper = new ItemTouchHelper(swipeToDeleteCallback);
        itemTouchhelper.attachToRecyclerView(recyclerView);
    }

    /**
     * Show the dialog for creating a feeding time, and send it to the
     * embedded controller for scheduling.
     */
    public void createFeedingTime(View view)
    {
        Log.d(TAG,"createFeedingTime");
        GregorianCalendar cal = new GregorianCalendar();

        final Dialog d = new Dialog(MainActivity.this);
        d.setTitle("NumberPicker");
        d.setContentView(R.layout.dialog);
        Button okButton = d.findViewById(R.id.okButton);
        Button cancelButton = d.findViewById(R.id.cancelButton);
        final NumberPicker np = d.findViewById(R.id.numberPicker1);
        final TimePicker tp = d.findViewById(R.id.timePicker1);
        tp.setIs24HourView(true);
        tp.setCurrentHour(cal.get(Calendar.HOUR_OF_DAY));
        tp.setCurrentMinute(cal.get(Calendar.MINUTE));
        np.setMaxValue(255);
        np.setMinValue(1);
        String [] displayedValues = new String[255];
        for(int x = 0;x<255;x++)
        {
            displayedValues[x] = Float.toString((x+1)/10.0f);
        }
        np.setDisplayedValues(displayedValues);
        np.setWrapSelectorWheel(false);
        okButton.setOnClickListener(v -> {
            Log.d(TAG,tp.getCurrentHour()+":"+tp.getCurrentMinute()+" "+np.getValue());
            boolean success = addFeedingTime(tp.getCurrentHour(),tp.getCurrentMinute(),np.getValue());
            d.dismiss();
            if(!success)
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage(R.string.could_not_create_message).setTitle(R.string.failure);
                AlertDialog alert = builder.create();
                alert.show();
            }
        });
        cancelButton.setOnClickListener(v -> d.dismiss());
        d.show();
    }

    /**
     * Send a feeding time to the embedded controller for scheduling.
     */
    private void createFeedingTime(FeedingTime ft)
    {
        Thread t = new Thread(() -> client.createFeedingTime(ft));
        t.start();
    }

    /**
     * Delete a feeding time from the embedded controller's schedule.
     */
    private void deleteFeedingTime(FeedingTime ft)
    {
        Thread t = new Thread(() -> client.deleteFeedingTime(ft));
        t.start();
    }

    /**
     * Create a feeding time and send it to the feeder.
     * Returns false if the feeding time could not be created.
     * @param hour the hour (local time) to start the feeding
     * @param minute the minute (local time) to start the feeding.
     * @param deciSeconds the duration of the feeding, in 0.1 second increments.
     */
    private boolean addFeedingTime(Integer hour, Integer minute, int deciSeconds)
    {
        if(client.getFeedingTimes().size() >= 18)
        {
            return false;
        }
        else
        {
            int slot = client.getAvailableSlot();
            if(slot < 0)
            {
                return false;
            }
            final FeedingTime ft = new FeedingTime(slot,hour,minute,deciSeconds/10.0f, false);
            createFeedingTime(ft);
            return true;
        }
    }

    @Override
    public void notifyUpdate()
    {
        // Redraw the list of feeding times
        new Handler(Looper.getMainLooper()).post(() -> {
            LinearLayoutManager linearLayout = new LinearLayoutManager(MainActivity.this);
            recyclerView.setLayoutManager(linearLayout);
            mAdapter = new FeedingTimeAdapter( client);
            recyclerView.setAdapter(mAdapter);

            enableSwipeToDeleteAndUndo();
        });
        // Remove progressbar
        if (this.progressBarHolder != null)
        {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    progressBarHolder.setVisibility(View.GONE);
                }
            });
        }
    }

    /**
     * Send a manual feeding command to the embedded hardware.
     */
    public void sendManual(View view)
    {
        Thread t = new Thread(() -> client.doManual(MANUAL_FEEDING_SECONDS));
        t.start();
    }
}