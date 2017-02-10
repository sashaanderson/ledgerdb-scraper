package ledgerdb.scraper.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class StatementDTO {
    
        @JsonProperty("statementDate")
        private String date;
        public String getDate() { return date; }
        private void setDate(String date) { this.date = date; }
        public void setDate(LocalDate date) {
            setDate(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        public void setDate(String date, String format) {
            setDate(LocalDate.parse(date, DateTimeFormatter.ofPattern(format)));
        }
        
        private int accountId;
        public int getAccountId() { return accountId; }
        public void setAccountId(int accountId) { this.accountId = accountId; }
        
        private BigDecimal amount;
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        
        private String description;
        public String getDescription() { return description; }
        public void setDescription(String description) {
            this.description = description
                    .replaceAll("\\s", " ")
                    .trim();
        }
        
        //TODO - remove column from db
        public final String source = "";
        
        int sequence;
        public int getSequence() { return sequence; }
        public void setSequence(int sequence) { this.sequence = sequence; }
        
        public boolean equalsExceptSequence(StatementDTO that) {
            return Objects.equal(this.date, that.date) &&
                    Objects.equal(this.accountId, that.accountId) &&
                    Objects.equal(this.amount, that.amount) &&
                    Objects.equal(this.description, that.description);
        }}
