package javabot.commands;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import com.antwerkz.maven.SPI;
import javabot.IrcEvent;
import javabot.Javabot;
import javabot.Message;
import javabot.dao.ConfigDao;
import javabot.model.Config;

/**
 * Created Jan 26, 2009
 *
 * @author <a href="mailto:jlee@antwerkz.com">Justin Lee</a>
 */
@SPI({AdminCommand.class})
public class DisableOperation extends OperationsCommand {
  @Param
  String name;

  @Inject
  private ConfigDao configDao;

  @Override
  public String getName() {
    return "DisableOperation";
  }

  @Override
  public List<Message> execute(final Javabot bot, final IrcEvent event) {
    final List<Message> responses = new ArrayList<Message>();
    if (bot.disableOperation(name)) {
      Config config = configDao.get();
      config.getOperations().remove(name);
      configDao.save(config);
      responses.add(new Message(event.getChannel(), event, name + " successfully disabled."));
      listCurrent(responses, bot, event);
    } else {
      responses.add(new Message(event.getChannel(), event, name + " not disabled.  Either it is not running"
          + " or it's not a valid name.  see listOperations for details."));
    }
    return responses;
  }
}