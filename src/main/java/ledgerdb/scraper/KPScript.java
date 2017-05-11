package ledgerdb.scraper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class KPScript {

    private static final String EOL = System.lineSeparator();
    
    private static final Logger logger = LogManager.getLogger();
    
    public static final String DEFAULT_KDBX_FILE = "ledgerdb-scraper.kdbx";
    public static final String DEFAULT_KDBX_PW = "@ledgerdb-scraper.pw";
    
    private final String file;
    private final String pw;
    
    public KPScript(String file, String pw) throws FileNotFoundException, IOException {
        if (!Files.exists(Paths.get(file)))
            throw new FileNotFoundException(file);
        this.file = file;
        
        if (pw != null && pw.startsWith("@")) {
            this.pw = Files.readAllLines(Paths.get(pw.substring(1))).get(0);
        } else {
            this.pw = pw;
        }
    }
    
    public String getEntry(String title, String field) throws IOException {
        return call("GetEntryString",
                "-ref-Title:" + title,
                "-FailIfNoEntry",
                "-Field:" + field);
    }
    
    public void listEntries() throws IOException {
        String output = call("ListEntries");
        String[] blocks = output.split(EOL + EOL);
        for (String block : blocks) {
            String[] lines = block.split(EOL);
            if (Arrays.stream(lines)
                    .anyMatch(line -> line.equals("GRPN: Recycle Bin")))
                continue;
            Arrays.stream(lines)
                    .filter(line -> line.startsWith("S: "))
                    .map(line -> line.replaceFirst("^(S: Password =) .*", "$1 ********"))
                    .forEach(System.out::println);
            System.out.println();
        }
    }
    
    public String call(String command, String... args) throws IOException {
        ArrayList<String> list = new ArrayList<>();
        list.add("KPScript");
        list.add("-c:" + command);
        list.add(file);
        list.add("-pw:" + pw);
        Collections.addAll(list, args);
        logger.debug(list
                .stream()
                .map(arg -> arg.startsWith("-pw:")
                        ? "-pw:******"
                        : arg)
                .collect(Collectors.joining(" ")));
        
        Process p = new ProcessBuilder(list)
                .redirectErrorStream(true)
                .start();
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted", e);
        }
        String output = IOUtils.toString(p.getInputStream(), Charset.defaultCharset());
        if (p.exitValue() != 0) {
            logger.error(output);
            throw new RuntimeException("KPScript failed, exit value " + p.exitValue());
        }
        
        String[] lines = output.split(EOL);
        if (lines.length < 2
                || !"OK: Operation completed successfully.".equals(lines[lines.length - 1])) {
            logger.error(output);
            throw new RuntimeException("KPScript failed");
        }
        return Arrays.stream(lines, 0, lines.length - 1).collect(Collectors.joining(EOL));
    }
}