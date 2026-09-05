package com.modernchat.service;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class PlayerMenuServiceTest
{
    @Test
    public void actionOpIsDerivedFromTheWidgetsActionsArray()
    {
        String[] actions = {
            "",
            "<col=ff00>Message</col>",
            "Add friend",
            "Add ignore",
            "Report"
        };

        assertEquals(3, actionOp(actions, "Add friend"));
        assertEquals(4, actionOp(actions, "Add ignore"));
        assertEquals(5, actionOp(actions, "Report"));
    }

    @Test
    public void actionOpStripsColourTagsAndWhitespace()
    {
        String[] actions = { " <col=ff00>Add friend</col> " };

        assertEquals(1, actionOp(actions, "Add friend"));
    }

    @Test
    public void actionOpReturnsMinusOneWhenActionIsMissing()
    {
        assertEquals(-1, actionOp(new String[] { "Message", "Add friend" }, "Report"));
    }

    @Test
    public void actionOpHandlesNullActions()
    {
        assertEquals(-1, actionOp(null, "Add friend"));
    }

    @Test
    public void actionOpAcceptsReportAbuseAsReport()
    {
        String[] actions = { "Message", "Add ignore", "Add friend", "Report abuse" };

        assertEquals(4, actionOp(actions, "Report"));
    }

    @Test
    public void actionOpIgnoresCaseOnBothSides()
    {
        String[] actions = { "ADD FRIEND" };

        assertEquals(1, actionOp(actions, "Add friend"));
    }

    private static int actionOp(String[] actions, String action)
    {
        try
        {
            Method method = PlayerMenuService.class.getDeclaredMethod("actionOp", String[].class, String.class);
            method.setAccessible(true);
            return (int) method.invoke(null, actions, action);
        }
        catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex)
        {
            throw new RuntimeException(ex);
        }
    }
}