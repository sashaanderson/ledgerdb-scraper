package ledgerdb.scraper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.openqa.selenium.io.IOUtils;

public class KPScript {

    public static final String DEFAULT_KDBX_FILE = "ledgerdb-scraper.kdbx";
    
    private static final String EOL = System.lineSeparator();
    
    private KPScript() {}
    
    public static String getEntry(String title, String field) throws IOException {
        String kdbxFile = System.getProperty("kdbx.file");
        if (kdbxFile == null)
            kdbxFile = DEFAULT_KDBX_FILE;
        if (!Files.exists(Paths.get(kdbxFile)))
            throw new FileNotFoundException(kdbxFile);
        
        String kdbxPw = System.getProperty("kdbx.pw");
        if (kdbxPw != null && kdbxPw.startsWith("@")) {
            kdbxPw = Files.readAllLines(Paths.get(kdbxPw.substring(1))).get(0);
        }
        
        ArrayList<String> args = new ArrayList<>();
        args.add("KPScript");
        args.add("-c:GetEntryString");
        args.add(kdbxFile);
        args.add("-pw:" + kdbxPw);
        args.add("-ref-Title:" + title);
        args.add("-FailIfNoEntry");
        args.add("-Field:" + field);
        System.out.println(args
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
            throw new IOException(e);
        }
        String output = IOUtils.readFully(p.getInputStream());
        if (p.exitValue() != 0) {
            System.err.println(output);
            throw new IOException("KPScript failed, exit value " + p.exitValue());
        }
        
        String[] lines = output.split(EOL);
        if (lines.length < 2
                || !"OK: Operation completed successfully.".equals(lines[lines.length - 1])) {
            System.err.println(output);
            throw new IOException("KPScript failed");
        }
        return Arrays.stream(lines, 0, lines.length - 1).collect(Collectors.joining(EOL));
    }
}
