package ledgerdb.scraper.institution.rbc;

import static com.google.common.base.Preconditions.checkState;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ledgerdb.scraper.ScraperDriverBase;
import ledgerdb.scraper.dto.StatementDTO;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class RbcScraperDriver extends ScraperDriverBase {
    
    private static final Logger logger = LogManager.getLogger();
    
    private boolean loggedIn = false;
    
    @Override
    public void run() {
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        
        logger.debug("Connecting to " + siteInfo.url);
        driver.get(siteInfo.url);
        
        logIn();
        
        for (int i = 0; ; i++) {
            WebElement e = driver.findElement(By.xpath("//section[@id='bankAcc']"));
            checkState("Bank Accounts".equals(e.findElement(By.xpath("./div/h3")).getText()));

            WebElement table = e.findElement(By.xpath("./table"));
            checkState("Bank Accounts Table".equals(table.findElement(By.xpath("./caption")).getText()));

            List<WebElement> links = table.findElements(By.xpath("./tbody/tr/th/form/a"));
            checkState(links.size() > 0);
            if (links.size() <= i)
                break;

            WebElement link = links.get(i);
            
            String ref = link.findElement(By.xpath("parent::form/following-sibling::span")).getText();
            // Chequing 12345-1234567
            logger.debug("Processing account " + (i + 1) + ": " + ref);
            Pattern pattern = Pattern.compile("^([A-Za-z]+) ([-0-9]+)$");
            Matcher matcher = pattern.matcher(ref);
            checkState(matcher.find());
            ref = matcher.group(2);
            ref = ref.replaceAll("[^0-9]", "");
            checkState(ref.matches("^[0-9]+$"));
            
            int accountId = serverSession.getAccountId(ref);
            
            link.click();
            
            //XXX
            //link = driver.findElement(By.xpath("//a[@title='Display last 30 days']"));
            //link.click();

            SLEEPER.sleepBetween(5, 10, TimeUnit.SECONDS); // wait for page to load
            e = driver.findElement(By.xpath("//section[@id='pdaTransactionsTable']"));
            checkState("Bank Account Details - RBC Online Banking".equals(driver.getTitle()));
            checkState(1 == e.findElements(By.xpath(".//table")).size());
            
            table = e.findElement(By.xpath("./table"));
            List<WebElement> rows = table.findElements(By.xpath(".//tr"));
            checkState(rows.size() > 0);
            checkState(rows.get(0).getText().equals("DATE DESCRIPTION WITHDRAWALS DEPOSIT BALANCE"));
            
            for (int j = 1; j < rows.size(); j++) {
                List<WebElement> cells = rows.get(j).findElements(By.xpath("./child::*")); // td/th cells
                checkState(cells.size() == 5);
                
                StatementDTO s = new StatementDTO();
                s.setAccountId(accountId);
                s.setDate(cells.get(0).getText(), "MMM d, yyyy");
                
                String description = cells.get(1).getText();
                description = description.replaceAll("\\p{Space}+", " ");
                s.setDescription(description);
                
                String amount1 = cells.get(2).getText(); // WITHDRAWALS, negative
                String amount2 = cells.get(3).getText(); // DEPOSIT, positive
                checkState(StringUtils.isBlank(amount1) != StringUtils.isBlank(amount2));
                String amount = StringUtils.isBlank(amount1) ? amount2 : amount1;
                checkState(amount.matches("^-?\\$[\\d,]+\\.\\d\\d$")); // -$1,000.00
                checkState(amount == (amount.startsWith("-") ? amount1 : amount2));
                amount = amount.replaceAll("[^-\\d.]", "");
                s.setAmount(new BigDecimal(amount));
                
                serverSession.merge(s);
                logger.debug("Done merged transaction " + j);
            }
            
            link = driver.findElement(By.xpath("//a[normalize-space(text())='Accounts Summary']"));
            link.click();
            checkStateAccountSummary();
        }
        
        logOut();
    }
    
    private void checkStateAccountSummary() {
        driver.findElement(By.xpath("//h2[starts-with(text(),'Welcome, ')]"));
        checkState(driver.getTitle().equals("Accounts Summary - RBC Online Banking"));
        logger.debug("Accounts Summary - RBC Online Banking");
    }
    
    private void logIn() {
        driver.findElement(By.xpath("//h2[text()='Sign in to Online Banking']"));
        logger.debug("Sign in to Online Banking");
        
        WebElement input;
        String id;
        input = driver.findElement(By.xpath("//label[starts-with(text(),'Client Card or Username')]"));
        id = input.getAttribute("for");
        checkState(id.matches("^\\w+$"));
        input = driver.findElement(By.xpath("//input[@id='" + id + "']"));
        checkState("text".equals(input.getAttribute("type")));
        input.sendKeys(siteInfo.logon);
        
        input = driver.findElement(By.xpath("//label[starts-with(text(),'Password')]"));
        id = input.getAttribute("for");
        checkState(id.matches("^\\w+$"));
        input = driver.findElement(By.xpath("//input[@id='" + id + "']"));
        checkState("password".equals(input.getAttribute("type")));
        input.sendKeys(siteInfo.password);
        
        input = driver.findElement(By.xpath("//button[text()='Sign In']"));
        checkState("submit".equals(input.getAttribute("type")));
        input.click();
        logger.debug("Logging in...");
        
        checkStateAccountSummary();
        
        logger.debug("Logged in...");
        this.loggedIn = true;
    }
    
    private void logOut() {
        logger.debug("Logging out...");
        
        WebElement button = driver.findElement(By.xpath("//button/span[.='Sign Out']/.."));
        button.click();
        
        // You've Successfully Signed Out of Online Banking
        driver.findElement(By.xpath("//h1[contains(., 'Successfully Signed Out')]"));
        
        logger.debug("Logged out");
        this.loggedIn = false;
    }
}