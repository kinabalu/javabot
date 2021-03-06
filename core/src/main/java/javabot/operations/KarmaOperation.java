package javabot.operations;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.antwerkz.maven.SPI;
import javabot.IrcEvent;
import javabot.IrcUser;
import javabot.Javabot;
import javabot.Message;
import javabot.dao.KarmaDao;
import javabot.model.Karma;
import javabot.operations.throttle.ThrottleItem;
import javabot.operations.throttle.Throttler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("UnusedDeclaration")
@SPI(BotOperation.class)
public class KarmaOperation extends BotOperation {
    @SuppressWarnings("UnusedDeclaration")
    private static final Logger log = LoggerFactory.getLogger(KarmaOperation.class);
    private static final Throttler<KarmaInfo> throttler = new Throttler<>(100, Javabot.THROTTLE_TIME);
    private static final Pattern optionPattern = Pattern.compile("\\s--\\p{Alpha}[\\p{Alnum}]*=");
    @Inject
    private KarmaDao dao;

    @Override
    public List<Message> handleMessage(final IrcEvent event) {
        final List<Message> responses = new ArrayList<>();
        responses.addAll(readKarma(event));
        if (responses.isEmpty()) {
            String message = event.getMessage();
            final IrcUser sender = event.getSender();
            final String channel = event.getChannel();
            int operationPointer = message.indexOf("++");
            boolean increment = true;
            if (operationPointer == -1) {
                operationPointer = message.indexOf("--");
                increment = false;
                // check for no karma inc/dec, and ~-- and ~++ too
                if (operationPointer < 1) {
                    return responses;
                }
            }

            /*
             * we won't get here unless operationPointer>0.
             *
             * But things get wonky; we need to handle two alternatives if it's a
             * karma decrement. One is: "admin --name=foo" and the other is
             * "foo --". We may need to apply a regex to ascertain whether
             * the signal is an option or not.
             *
             * The regex assumes options look like "--foo="
             */
            if (!increment) {
                final String potentialParam = message.substring(operationPointer-1);
                Matcher matcher = optionPattern.matcher(potentialParam);
                if (matcher.find()) {
                    // we PRESUMABLY have an option...
                    return responses;
                }
            }
            final String nick;
            try {
                nick = message.substring(0, operationPointer).trim().toLowerCase();
            } catch (StringIndexOutOfBoundsException e) {
                log.info("message = " + message, e);
                throw e;
            }
            // got an empty nick; spaces only?
            if (nick.isEmpty()) {
                return responses;
            }
            if (!channel.startsWith("#")) {
                responses.add(new Message(channel, event, "Sorry, karma changes are not allowed in private messages."));
            }
            if (responses.isEmpty()) {
                if (throttler.isThrottled(new KarmaInfo(sender, nick))) {
                    responses.add(new Message(channel, event, "Rest those fingers, Tex"));
                } else {
                    throttler.addThrottleItem(new KarmaInfo(sender, nick));
                    if (nick.equalsIgnoreCase(sender.getNick())) {
                        if (increment) {
                            responses.add(new Message(channel, event, "You can't increment your own karma."));
                        }
                        increment = false;
                    }
                    Karma karma = dao.find(nick);
                    if (karma == null) {
                        karma = new Karma();
                        karma.setName(nick);
                    }
                    if (increment) {
                        karma.setValue(karma.getValue() + 1);
                    } else {
                        karma.setValue(karma.getValue() - 1);
                    }
                    karma.setUserName(sender.getNick());
                    dao.save(karma);
                    responses.addAll(readKarma(new IrcEvent(event.getChannel(), event.getSender(), "karma " + nick)));
                }
            }
        }
        return responses;
    }

    public List<Message> readKarma(final IrcEvent event) {
        final String message = event.getMessage();
        final String channel = event.getChannel();
        final IrcUser sender = event.getSender();
        final List<Message> response = new ArrayList<>();
        if (message.startsWith("karma ")) {
            final String nick = message.substring("karma ".length()).toLowerCase();
            final Karma karma = dao.find(nick);
            if (karma != null) {
                if (nick.equalsIgnoreCase(sender.getNick())) {
                    response.add(new Message(channel, event,
                            sender + ", you have a karma level of " + karma.getValue()));
                } else {
                    response.add(new Message(channel, event,
                            nick + " has a karma level of " + karma.getValue() + ", " + sender));
                }
            } else {
                response.add(new Message(channel, event, nick + " has no karma, " + sender));
            }
        }
        return response;
    }

    public static final class KarmaInfo implements ThrottleItem<KarmaInfo> {
        private final IrcUser user;
        private final String target;

        public KarmaInfo(final IrcUser user, final String target) {
            this.user = user;
            this.target = target;
        }

        public boolean matches(final KarmaInfo ki) {
            return user.equals(ki.user) && target.equals(ki.target);
        }
    }
}