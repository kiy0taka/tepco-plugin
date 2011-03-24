package org.jenkinsci.plugins.tepco;

import static java.util.regex.Pattern.compile;
import hudson.Extension;
import hudson.Plugin;
import hudson.PluginWrapper;
import hudson.model.PeriodicWork;
import hudson.model.Hudson;
import hudson.widgets.Widget;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

@Extension
public class TepcoWidget extends Widget {

    private Date lastUpdated;

    private List<UsageCondition> usages;

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public List<UsageCondition> getUsages() {
        return usages;
    }

    public void setUsages(List<UsageCondition> usages) {
        this.usages = usages;
    }

    public static class UsageCondition {

        private Date date;
        private int today;
        private int yesterday;

        public UsageCondition(Date date, int today, int yesterday) {
            this.date = date;
            this.today = today;
            this.yesterday = yesterday;
        }

        public Date getDate() { return date; }
        public int getToday() { return today; }
        public int getYesterday() { return yesterday; }
    }

    @Extension
    public static class CsvDownloader extends PeriodicWork {

        private static final String CSV_URL = "http://www.tepco.co.jp/forecast/html/images/juyo-j.csv";

        private static final Pattern HEADER_PATTERN = compile("(\\d{4}/\\d{1,2}/\\d{1,2} \\d{1,2}:\\d{1,2}) UPDATE");

        private static final Pattern DATA_PATTERN = compile("(\\d{4}/\\d{1,2}/\\d{1,2}),(\\d{1,2}:\\d{1,2}),(\\d+),(\\d+)");

        @Override
        public long getRecurrencePeriod() {
            return 10 * 60 * 1000;
        }

        @Override
        public long getInitialDelay() {
            return 0;
        }

        @Override
        protected void doRun() throws Exception {
            boolean isFirst = true;
            DateFormat fmt = new SimpleDateFormat("yyyy/M/d H:m");
            Date lastUpdated = null;
            List<UsageCondition> usages = new ArrayList<UsageCondition>();
            for (String line : loadCsv().split("\r?\n")) {
                if (isFirst) {
                    Matcher h = HEADER_PATTERN.matcher(line);
                    if (h.matches()) {
                        lastUpdated = fmt.parse(h.group(1));
                    }
                    isFirst = false;
                } else {
                    Matcher d = DATA_PATTERN.matcher(line);
                    if (d.matches()) {
                        usages.add(new UsageCondition(
                            fmt.parse(String.format("%s %s", d.group(1), d.group(2))),
                            Integer.parseInt(d.group(3)),
                            Integer.parseInt(d.group(4))));
                    }
                }
            }
            if (lastUpdated != null) {
                for (Widget w : Hudson.getInstance().getWidgets()) {
                    if (w instanceof TepcoWidget) {
                        TepcoWidget tw = (TepcoWidget) w;
                        tw.setLastUpdated(lastUpdated);
                        tw.setUsages(usages);
                    }
                }
            }
        }

        protected String loadCsv() {
            InputStream is = null;
            try {
                is = new URL(CSV_URL).openStream();
                return IOUtils.toString(is, "Shift_JIS");
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
    }
}
