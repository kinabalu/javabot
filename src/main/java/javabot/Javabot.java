package javabot;

import javabot.dao.ChangesDao;
import javabot.dao.ChannelDao;
import javabot.dao.FactoidDao;
import javabot.dao.KarmaDao;
import javabot.dao.LogDao;
import javabot.dao.SeenDao;
import javabot.model.Logs;
import javabot.operations.AddFactoidOperation;
import javabot.operations.BotOperation;
import javabot.operations.DictOperation;
import javabot.operations.ForgetFactoidOperation;
import javabot.operations.GetFactoidOperation;
import javabot.operations.GuessOperation;
import javabot.operations.JavadocOperation;
import javabot.operations.KarmaChangeOperation;
import javabot.operations.KarmaReadOperation;
import javabot.operations.LeaveOperation;
import javabot.operations.LiteralOperation;
import javabot.operations.QuitOperation;
import javabot.operations.SeenOperation;
import javabot.operations.SpecialCasesOperation;
import javabot.operations.StatsOperation;
import javabot.operations.TellOperation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Javabot extends PircBot implements ChannelControl, Responder {
    private static final Log log = LogFactory.getLog(Javabot.class);
    private final Map<String, String> channelPreviousMessages = new HashMap<String, String>();
    private List<BotOperation> operations;
    private String host;
    private String dictHost;
    private String nickName;
    private int port;
    private String javadocSources;
    private String javadocBaseUrl;
    private String[] startStrings;
    private int authWait;
    private String password;
    private List<String> channels = new ArrayList<String>();
    private List<String> ignores = new ArrayList<String>();
    public static final int PORT_NUMBER = 2347;
    public static final String JAVABOT_PROPERTIES = "javabot.properties";
    private ApplicationContext context = new ClassPathXmlApplicationContext("classpath:applicationContext.xml");
    private FactoidDao factoidDao = (FactoidDao) context.getBean("factoidDao");
    private ChangesDao changeDao = (ChangesDao) context.getBean("changesDao");
    private SeenDao seenDao = (SeenDao) context.getBean("seenDao");
    private LogDao logDao = (LogDao) context.getBean("logDao");
    private ChannelDao channelDao = (ChannelDao) context.getBean("channelDao");
    private KarmaDao karmaDao = (KarmaDao) context.getBean("karmaDao");

    public Javabot() throws JDOMException, IOException {
        setName("javabot");
        setLogin("javabot");
        setVersion("Javabot 2.0");
        loadConfig();
    }

    private void loadConfig() throws JDOMException, IOException {
        SAXBuilder reader = new SAXBuilder(true);
        Document document = null;
        try {
            reader.setValidation(false);
            File configFile = getConfigFile();
            if (configFile != null) {
                document = reader.build(configFile);
                Element root = document.getRootElement();
                loadServerInfo(root);
                loadJavadocInfo(root);
                loadDictInfo(root);
                loadNickName(root);
                loadChannelInfo(root);
                loadAuthenticationInfo(root);
                loadStartStringInfo(root);
                loadOperationInfo(root);
                loadIgnoreInfo(root);
            }
        } catch (Exception e) {
            log.info(e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        }
    }

    protected File getConfigFile() {
        return new File(new File(System.getProperty("user.home")), ".javabot/config.xml").getAbsoluteFile();
    }

    private void loadIgnoreInfo(Element root) {
        List<Element> ignoreNodes = new ArrayList<Element>();
        for (Object element : root.getChildren("ignore")) {
            ignoreNodes.add((Element) element);
        }
        for (Element node : ignoreNodes) {
            ignores.add(node.getAttributeValue("name"));
        }
    }

    private void loadOperationInfo(Element root) {
        List operationNodes = root.getChildren("operation");
        Iterator iterator = operationNodes.iterator();
        operations = new ArrayList<BotOperation>();
        while (iterator.hasNext()) {
            Element node = (Element) iterator.next();
            try {
                Class operationClass = Class.forName(node.getAttributeValue("class"));
                if (DictOperation.class.equals(operationClass)) {
                    operations.add(new DictOperation(dictHost));
                } else if (JavadocOperation.class.equals(operationClass)) {
                    operations.add(new JavadocOperation(javadocSources, javadocBaseUrl));
                } else if (LeaveOperation.class.equals(operationClass)) {
                    operations.add(new LeaveOperation(this));
                } else if (LiteralOperation.class.equals(operationClass)) {
                    operations.add(new LiteralOperation(factoidDao));
                } else if (QuitOperation.class.equals(operationClass)) {
                    operations.add(new QuitOperation(getNickPassword()));
                } else if (SpecialCasesOperation.class.equals(operationClass)) {
                    operations.add(new SpecialCasesOperation(this));
                } else if (TellOperation.class.equals(operationClass)) {
                    operations.add(new TellOperation(getNick(), this));
                } else if (AddFactoidOperation.class.equals(operationClass)) {
                    operations.add(new AddFactoidOperation(factoidDao, changeDao));
                } else if (ForgetFactoidOperation.class.equals(operationClass)) {
                    operations.add(new ForgetFactoidOperation(factoidDao, changeDao));
                } else if (GuessOperation.class.equals(operationClass)) {
                    operations.add(new GuessOperation(factoidDao));
                } else if (GetFactoidOperation.class.equals(operationClass)) {
                    operations.add(new GetFactoidOperation(factoidDao));
                } else if (KarmaChangeOperation.class.equals(operationClass)) {
                    operations.add(new KarmaChangeOperation(karmaDao, changeDao, this));
                } else if (KarmaReadOperation.class.equals(operationClass)) {
                    operations.add(new KarmaReadOperation(karmaDao));
                } else if (StatsOperation.class.equals(operationClass)) {
                    operations.add(new StatsOperation(factoidDao));
                } else if (SeenOperation.class.equals(operationClass)) {
                    operations.add(new SeenOperation(seenDao));
                } else {
                    operations.add((BotOperation) operationClass.newInstance());
                }
                log.debug(operations.get(operations.size() - 1));
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    private void loadStartStringInfo(Element root) {
        List startNodes = root.getChildren("message");
        Iterator iterator = startNodes.iterator();
        startStrings = new String[startNodes.size()];
        int index = 0;
        while (iterator.hasNext()) {
            Element node = (Element) iterator.next();
            startStrings[index] = node.getAttributeValue("tag");
            index++;
        }
    }

    private void loadAuthenticationInfo(Element root) {
        Element authNode = root.getChild("auth");
        authWait = Integer.parseInt(authNode.getAttributeValue("wait"));
        setNickPassword(authNode.getAttributeValue("password"));
        Element nickNode = root.getChild("nick");
        setName(nickNode.getAttributeValue("name"));
    }

    private void loadChannelInfo(Element root) {
        List channelNodes = root.getChildren("channel");
        for (Object channelNode : channelNodes) {
            Element node = (Element) channelNode;
            channels.add(node.getAttributeValue("name"));
        }
    }

    private void loadDictInfo(Element root) {
        Element dictNode = root.getChild("dict");
        dictHost = dictNode.getAttributeValue("host");
    }

    private void loadNickName(Element root) {
        Element nickNode = root.getChild("nick");
        nickName = nickNode.getAttributeValue("name");
    }

    private void loadJavadocInfo(Element root) {
        Element javadocNode = root.getChild("javadoc");
        javadocSources = javadocNode.getAttributeValue("reference-xml");
        if (javadocSources == null) {
            throw new IllegalStateException(
                    "The config file must supply a reference-xml attribute, as per the config.xml.sample file.");
        }
        javadocBaseUrl = javadocNode.getAttributeValue("base-url");
    }

    private void loadServerInfo(Element root) {
        Element serverNode = root.getChild("server");
        host = serverNode.getAttributeValue("name");
        port = Integer.parseInt(serverNode.getAttributeValue("port"));
    }

    public void main(String[] args) throws IOException, JDOMException {
        log.info("Starting Javabot");
        Javabot bot = new Javabot();
        new PortListener(PORT_NUMBER, bot.getNickPassword()).start();
        bot.setMessageDelay(2000);
        bot.connect();
    }

    @SuppressWarnings({"EmptyCatchBlock"})
    private void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
        }
    }

    @SuppressWarnings({"StringContatenationInLoop"})
    public void connect() {
        boolean connected = false;
        while (!connected) {
            try {
                log.debug(host + ":" + port);
                connect(host, port);
                sendRawLine("PRIVMSG NickServ :identify " + getNickPassword());
                sleep(authWait);
                for (String channel : channels) {
                    joinChannel(channel);
                }
                connected = true;
            } catch (Exception exception) {
                log.error(exception.getMessage(), exception);
            }
            sleep(1000);
        }
    }

    @Override
    public void onMessage(String channel, String sender, String login, String hostname, String message) {
        seenDao.logSeen(sender, channel, "said: " + message);
        if (channelDao.getChannel(channel).getChannel() != null && channelDao.getChannel(channel).getLogged()) {
            logDao.logMessage(Logs.Type.MESSAGE, sender, channel, message);
        }
        log.info("Message " + message);
        if (isValidSender(sender)) {
            for (String startString : startStrings) {
                int length = startString.length();
                if (message.startsWith(startString)) {
                    handleAnyMessage(channel, sender, login, hostname, message.substring(length).trim());
                    return;
                }
            }
            handleAnyChannelMessage(channel, sender, login, hostname, message);
        } else {
            log.info("ignoring " + sender);
        }
    }

    public List<Message> getResponses(String channel, String sender, String login, String hostname, String message) {
        log.info("getResponses " + message);
        for (BotOperation operation : operations) {
            List<Message> messages = operation.handleMessage(new BotEvent(channel, sender, login, hostname, message));
            if (!messages.isEmpty()) {
                return messages;
            }
        }
        return null;
    }

    public List getChannelResponses(String channel, String sender, String login, String hostname, String message) {
        log.info("getChannelResponses " + message);
        for (BotOperation operation : operations) {
            List messages = operation.handleChannelMessage(new BotEvent(channel, sender, login, hostname, message));
            if (!messages.isEmpty()) {
                return messages;
            }
        }
        return null;
    }

    @SuppressWarnings({"StringContatenationInLoop"})
    private void handleAnyMessage(String channel, String sender, String login, String hostname, String message) {
        List messages = getResponses(channel, sender, login, hostname, message);
        if (messages != null) {
            for (Object message1 : messages) {
                Message nextMessage = (Message) message1;
                if (nextMessage.isAction()) {
                    sendAction(nextMessage.getDestination(), nextMessage.getMessage());
                    seenDao.logSeen(nickName, nextMessage.getDestination(), "did a /me " + nextMessage.getMessage());
                    if (channelDao.getChannel(channel).getChannel() != null && channelDao.getChannel(channel)
                            .getLogged()) {
                        logDao.logMessage(Logs.Type.ACTION, nickName, nextMessage.getDestination(),
                                "ACTION:" + nextMessage.getMessage());
                    }
                } else {
                    sendMessage(nextMessage.getDestination(), nextMessage.getMessage());
                    seenDao.logSeen(nickName, nextMessage.getDestination(), "said: " + nextMessage.getMessage());
                    if (channelDao.getChannel(channel).getChannel() != null && channelDao.getChannel(channel)
                            .getLogged()) {
                        logDao.logMessage(Logs.Type.MESSAGE, nickName, nextMessage.getDestination(),
                                nextMessage.getMessage());
                    }
                }
            }
        }
        channelPreviousMessages.put(channel, message);
    }

    @SuppressWarnings({"StringContatenationInLoop"})
    private void handleAnyChannelMessage(String channel, String sender, String login, String hostname, String message) {
        List messages = getChannelResponses(channel, sender, login, hostname, message);
        if (messages != null) {
            for (Object message1 : messages) {
                Message nextMessage = (Message) message1;
                if (nextMessage.isAction()) {
                    sendAction(nextMessage.getDestination(), nextMessage.getMessage());
                    seenDao.logSeen(nickName, nextMessage.getDestination(), "did a /me " + nextMessage.getMessage());
                    if (channelDao.getChannel(channel).getChannel() != null && channelDao.getChannel(channel)
                            .getLogged()) {
                        logDao.logMessage(Logs.Type.ACTION, nickName, nextMessage.getDestination(),
                                "ACTION:" + nextMessage.getMessage());
                    }
                } else {
                    sendMessage(nextMessage.getDestination(), nextMessage.getMessage());
                    seenDao.logSeen(nickName, nextMessage.getDestination(), "said: " + nextMessage.getMessage());
                    if (channelDao.getChannel(channel).getChannel() != null && channelDao.getChannel(channel)
                            .getLogged()) {
                        logDao.logMessage(Logs.Type.MESSAGE, nickName, nextMessage.getDestination(),
                                nextMessage.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public void onInvite(String targetNick, String sourceNick, String sourceLogin, String sourceHostname,
                         String channel) {
        if (channels.contains(channel)) {
            joinChannel(channel);
        }
    }

    @Override
    public void onDisconnect() {
        connect();
    }

    public String getPreviousMessage(String channel) {
        if (channelPreviousMessages.containsKey(channel)) {
            return channelPreviousMessages.get(channel);
        }
        return "";
    }

    public boolean isOnSameChannelAs(String nick) {
        for (String channel : getChannels()) {
            if (userIsOnChannel(nick, channel)) {
                return true;
            }
        }
        return false;
    }

    public boolean userIsOnChannel(String nick, String channel) {
        for (User user : getUsers(channel)) {
            if (user.getNick().toLowerCase().equals(nick.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onPrivateMessage(String sender, String login, String hostname, String message) {
        if (isOnSameChannelAs(sender)) {
            handleAnyMessage(sender, sender, login, hostname, message);
            logDao.logMessage(Logs.Type.MESSAGE, sender, sender, message);
        }
    }

    @Override
    public void onJoin(String channel, String sender, String login, String hostname) {
        seenDao.logSeen(sender, channel, "joined the channel");
        if (channelDao.getChannel(channel).getChannel() != null && channelDao.getChannel(channel).getLogged()) {
            logDao.logMessage(Logs.Type.JOIN, sender, channel, "joined the channel");
        }
    }

    @Override
    public void onQuit(String channel, String sender, String login, String hostname) {
        seenDao.logSeen(sender, channel, "quit");
        if (channelDao.getChannel(channel).getChannel() != null && channelDao.getChannel(channel).getLogged()) {
            logDao.logMessage(Logs.Type.QUIT, sender, channel, "quit");
        }
    }

    @Override
    public void onPart(String channel, String sender, String login, String hostname) {
        seenDao.logSeen(sender, channel, "parted the channel");
        if (channelDao.getChannel(channel).getChannel() != null && channelDao.getChannel(channel).getLogged()) {
            logDao.logMessage(Logs.Type.PART, sender, channel, "parted the channel");
        }

    }

    @Override
    public void onAction(String sender, String login, String hostname, String target, String action) {
        seenDao.logSeen(sender, target, "did a /me " + action);
        if (channelDao.getChannel(target).getChannel() != null && channelDao.getChannel(target).getLogged()) {
            logDao.logMessage(Logs.Type.ACTION, sender, target, action);
        }
    }

    @Override
    public void onKick(String channel, String kickerNick, String kickerLogin, String kickerHostname,
                       String recipientNick, String reason) {
        seenDao.logSeen(recipientNick, channel,
                kickerNick + " kicked " + recipientNick + " with this reasoning: " + reason);
        if (channelDao.getChannel(channel).getChannel() != null && channelDao.getChannel(channel).getLogged()) {
            logDao.logMessage(Logs.Type.KICK, kickerNick, channel, " kicked " + recipientNick + " (" + reason + ")");
        }
    }

    public void onOp() {
    }

    public String getDictHost() {
        return dictHost;
    }

    public String getJavadocSources() {
        return javadocSources;
    }

    public String getJavadocBaseUrl() {
        return javadocBaseUrl;
    }

    public void setNickPassword(String value) {
        password = value;
    }

    public String getNickPassword() {
        return password;
    }

    private boolean isValidSender(String sender) {
        return !ignores.contains(sender);
    }

    public void addIgnore(String sender) {
        ignores.add(sender);
    }

    public void shutdown() {
        disconnect();

    }

    @Override
    public void log(String string) {
        log.info(string);
    }
}
