package st.crosscheck.fishfeeder.data;

import java.util.Locale;
import java.util.TimeZone;

import androidx.annotation.NonNull;

/**
 * A data class which encapsulates a feeding time (in hours and minutes since midnight, UTC) and the
 * amount of food to dispense (in seconds of running the motor) and the slot position in the list of
 * feeding times.
 *
 * @author Erik Berglund
 */
public class FeedingTime implements Comparable<FeedingTime>
{
    public int slot;
    public int hour;
    public int minute;
    public float seconds;

    /**
     * Create a feeding time.
     * @param slot The slot number on the feeder.
     * @param hour the hour after midnight, in 24-hour format
     * @param minute the minute after the full hour
     * @param seconds the number of seconds to run the feeder
     * @param fromUTC if true, the parameters are in UTC, otherwise in the local timezone.
     */
    public FeedingTime(int slot, int hour, int minute, float seconds, boolean fromUTC)
    {
        this.slot = slot;
        this.seconds = seconds;
        this.hour = hour;
        this.minute = minute;
        if(!fromUTC)
        {
            this.setMinutesSinceMidnight(this.getMinutesSinceMidnight()-getOffsetMinutes());
        }
    }

    /**
     * Get the offset, in minutes, between UTC and local time.
     */
    private int getOffsetMinutes()
    {
        return TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000;
    }

    public byte getDeciSeconds()
    {
        return (byte)Math.round(seconds*10);
    }

    @NonNull
    @Override
    public String toString()
    {
        return slot+" "+hour + ":" + minute + " " + seconds ;
    }

    private int getMinutesSinceMidnight()
    {
        return hour*60+minute;
    }

    private void setMinutesSinceMidnight(int minutes)
    {
        // Handle times before midnight
        minutes = clampMinutes(minutes);
        hour = minutes / 60;
        this.minute = minutes%60;
    }

    private int getMinutesSinceMidnightLocalTime()
    {
        return clampMinutes(hour*60+minute+getOffsetMinutes());
    }

    /**
     * Ensure that the number of minutes is in the range 0 - (24*60-1) (inclusive).
     */
    private int clampMinutes(int minutes)
    {
        while(minutes < 0)
        {
            minutes += 60*24;
        }
        // Roll over at midnight
        minutes %= 24*60;
        return minutes;
    }

    @Override
    public int compareTo(FeedingTime feedingTime)
    {
        return getMinutesSinceMidnightLocalTime()-feedingTime.getMinutesSinceMidnightLocalTime();
    }

    public String getFormattedTime()
    {
        // Compute the offset from milliseconds to hours and minutes
        int totalMinutes = getMinutesSinceMidnightLocalTime();
        int hours = totalMinutes/60;
        int minutes = totalMinutes%60;
        return String.format(Locale.getDefault(),"%02d:%02d",hours,minutes);
    }
}
