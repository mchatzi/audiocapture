package com.didi.splviewer;

import org.junit.Assert;
import org.junit.Test;

public class Average {
    private long numberOfItems = 0;
    private double average = 0;

    public double newAverage(double number) {
        return average = average * ((double) numberOfItems / (numberOfItems + 1)) + (number / (++numberOfItems));
    }

    public double average() {
        return average;
    }

    public void reset() {
        numberOfItems = 0;
        average = 0;
    }


    //Forgetting how many items participate in this average means that the next number coming in will influence the average more.
    //In other words by forgetting we make the average mechanism favor more new statistics coming in and forget old ones.
    //Newer statistics matter more than old ones
    //TODO Revisit, does this really do that?
    public void forget() {
        numberOfItems /= 4;
    }

    @Test
    public void test() {
        Average a = new Average();
        a.newAverage(2);
        a.newAverage(3);
        a.newAverage(4);
        a.newAverage(6);
        a.newAverage(7);
        Assert.assertEquals(5, a.newAverage(8), 0);
    }
}
