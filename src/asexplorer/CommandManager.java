package asexplorer;

import asexplorer.command.CommandBase;
import gnu.getopt.LongOpt;
import java.net.URL;
import java.rmi.RMISecurityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.naming.InitialContext;
import org.nocrala.tools.texttablefmt.Table;

/**
 *
 * @author unixo
 */
public class CommandManager
{
    protected HashMap<String, CommandBase> allCommands;

    private CommandManager()
    {
        allCommands = new HashMap<>();
    }

    public static CommandManager getInstance()
    {
        return CommandManagerHolder.INSTANCE;
    }

    private static class CommandManagerHolder
    {
        private static final CommandManager INSTANCE = new CommandManager();
    }

    /**
     * Builds the list of all available commands (all classes derived from
     * CommandBase)
     */
    public void loadCommands()
    {
        try {
            Class[] classes = ClassFinder.getClassesInPackage("asexplorer.command");

            for (Class c : classes) {
                if (c.getSuperclass().equals(CommandBase.class)) {
                    CommandBase aCmd = (CommandBase) c.newInstance();
                    allCommands.put(aCmd.getCommandName(), aCmd);
                }
            }
        } catch (InstantiationException | IllegalAccessException ex) {
            ASExplorer.logger.error("Unable to load commands");
        }
    }
    
    public void displayCommandHelp(String aCommand) {        
        for (Map.Entry<String, CommandBase> entry : this.allCommands.entrySet()) {
            CommandBase cmd = entry.getValue();
            if (cmd.getCommandName().equals(aCommand)) {                
                String help = "Usage: java -jar ASExplorer [...] --command " + aCommand + " " + cmd.getHelp();
                System.out.println(help);
                break;
            }
        }        
    }

    public void displayCommandList()
    {
        TreeSet<String> keys = new TreeSet<>(allCommands.keySet());

        if (keys.isEmpty()) {
            System.err.println("No commands available");
        } else {
            Table t = new Table(2);
            t.addCell("Command");
            t.addCell("Description");
            
            for (String key : keys) {
                CommandBase value = allCommands.get(key);
                
                t.addCell(key);
                t.addCell(value.getDescription());
            }
            System.out.println(t.render());
        }
    }

    /**
     * Asks all commands to provide command-line arguments, if any
     *
     * @return List of all additional parameters
     */
    public List<LongOpt> getCommandParameters()
    {
        ArrayList<LongOpt> params = new ArrayList<>();
        TreeSet<String> keys = new TreeSet<>(allCommands.keySet());

        for (String key : keys) {
            CommandBase value = allCommands.get(key);
            ArrayList<LongOpt> cmdParams = value.getParameters();
            if (cmdParams != null) {
                params.addAll(cmdParams);
            }
        }

        return params;
    }

    public boolean parseParameter(String param, String value)
    {
        TreeSet<String> keys = new TreeSet<>(allCommands.keySet());

        for (String key : keys) {
            CommandBase aCmd = allCommands.get(key);

            if (aCmd.parseParameter(param, value)) {
                return true;
            }
        }

        return false;
    }

    public void exec()
    {
        String aCmdName = Config.getInstance().getCommand();

        if (aCmdName == null || allCommands.keySet().contains(aCmdName) == false) {
            System.err.println("No command was specified\n");
        } else {
            /**
             * Setup policy file
             * @todo permissions are too open!
             */
            ClassLoader cl = getClass().getClassLoader();
            URL policyURL =cl.getResource("asexplorer/client.policy");
            System.setProperty("java.security.policy",policyURL.toString());
            if (System.getSecurityManager() == null) {
                System.setSecurityManager(new RMISecurityManager());
            }

            // Create initial context and connect to application server
            CommandBase aCommand = allCommands.get(aCmdName);
            InitialContext ctx = Config.getInstance().getServerType().getInitialContext();

            // Execute selected command
            if (ctx != null) {
                aCommand.exec(ctx);
            }
        }
    }
}
