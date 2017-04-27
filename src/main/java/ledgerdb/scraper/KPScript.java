package ledgerdb.scraper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class KPScript {

    public static final String DEFAULT_KDBX_FILE = "ledgerdb-scraper.kdbx";
    public static final String DEFAULT_KDBX_PW = "@ledgerdb-scraper.pw";
    
    private static final String EOL = System.lineSeparator();
    
    private static final Logger logger = LogManager.getLogger();
    
    private KPScript() {}
    
    public static String getEntry(String title, String field) throws IOException {
        String file = System.getProperty("kdbx.file", DEFAULT_KDBX_FILE);
        if (!Files.exists(Paths.get(file)))
            throw new FileNotFoundException(file);
        
        String pw = System.getProperty("kdbx.pw", DEFAULT_KDBX_PW);
        if (pw != null && pw.startsWith("@")) {
            pw = Files.readAllLines(Paths.get(pw.substring(1))).get(0);
        }
        
        ArrayList<String> args = new ArrayList<>();
        args.add("KPScript");
        args.add("-c:GetEntryString");
        args.add(file);
        args.add("-pw:" + pw);
        args.add("-ref-Title:" + title);
        args.add("-FailIfNoEntry");
        args.add("-Field:" + field);
        logger.debug(args
                .stream()
                .map(arg -> arg.startsWith("-pw:")
                        ? "-pw:******"
                        : arg)
                .collect(Collectors.joining(" ")));
        
        Process p = new ProcessBuilder(args)
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