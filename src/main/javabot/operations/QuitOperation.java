package javabot.operations;

import com.rickyclarkson.java.lang.Debug;

import java.util.ArrayList;
import java.util.List;

import javabot.BotEvent;
import javabot.Message;

import com.rickyclarkson.java.util.TypeSafeList;

public class QuitOperation implements BotOperation
{
	private final String password;

	public QuitOperation(String password)
	{
		this.password=password;
	}
	
	public List<Message> handleMessage(BotEvent event)
	{
		List<Message> messages=new ArrayList<Message>();

		String message=event.getMessage();

		if (message.toLowerCase().startsWith("quit "))
			if (message.substring("quit ".length()).equals(password))
			{
				Debug.printDebug("About to quit");
				System.exit(0);
			}

                return messages;
	}

	public List<Message> handleChannelMessage(BotEvent event)
	{
			return new ArrayList<Message>();
	}
}
