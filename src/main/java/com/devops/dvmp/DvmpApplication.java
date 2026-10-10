package com.devops.dvmp;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class DvmpApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(DvmpApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(DvmpApplication.class, args);
    }

    /** Seed three sample datasets (with real files) on first start. */
    @Bean
    CommandLineRunner seed(DatasetRepository repo, FileStorageService storage) {
        return args -> {
            if (repo.count() > 0) return;

            Dataset d1 = new Dataset("Customer Churn 2024",
                "Anonymised records of 1,000 telecom customers with their plan, monthly charge, tenure and whether "
                + "they left the company. Collected from the billing system and cleaned of duplicates.\n\n"
                + "Use it to train classification models that predict churn, or to study how plan type affects retention. "
                + "Each row is one customer; the target column is `churned` (1 = left, 0 = stayed).",
                "CSV", "user");
            d1.setSubtitle("Telecom customers and whether they churned");
            d1.setTags("telecom,churn,classification,tabular");
            d1.setLicense("CC0: Public Domain");
            d1.addVersion(new DatasetVersion(1, "Initial import", storage.storeBytes("churn_v1.csv", churn(30, true)), "user"));
            d1.addVersion(new DatasetVersion(2, "Removed duplicate rows", storage.storeBytes("churn_v2.csv", churn(28, false)), "user"));

            Dataset d2 = new Dataset("Sales Forecast Data",
                "Monthly unit sales and revenue per region for the last two years. Intended for time-series forecasting practice.",
                "CSV", "user2");
            d2.setSubtitle("Monthly sales per region for forecasting");
            d2.setTags("sales,forecasting,time-series");
            d2.setLicense("CC BY 4.0");
            d2.addVersion(new DatasetVersion(1, "Initial import", storage.storeBytes("sales.csv", sales()), "user2"));
            d2.setStatus(Status.IN_REVIEW);

            Dataset d3 = new Dataset("Iris Flower Measurements",
                "The classic Iris dataset: sepal and petal measurements for three iris species. A small, clean dataset "
                + "that is ideal for learning classification.",
                "CSV", "user2");
            d3.setSubtitle("Classic beginner dataset for classification");
            d3.setTags("beginner,classification,biology");
            d3.setLicense("CC0: Public Domain");
            d3.addVersion(new DatasetVersion(1, "Initial import", storage.storeBytes("iris.csv", iris()), "user2"));
            d3.setStatus(Status.APPROVED);
            d3.setReviewedBy("reviewer");
            d3.setReviewNote("Clean data, good description. Approved.");

            repo.save(d1);
            repo.save(d2);
            repo.save(d3);
        };
    }

    private static byte[] churn(int rows, boolean duplicates) {
        StringBuilder sb = new StringBuilder("customer_id,age,plan,monthly_charge,tenure_months,churned\n");
        String[] plans = {"basic", "pro", "premium"};
        for (int i = 1; i <= rows; i++) {
            sb.append(String.format(java.util.Locale.ROOT, "%d,%d,%s,%.2f,%d,%d%n",
                    1000 + i, 20 + (i * 7) % 45, plans[i % 3], 299.0 + i * 10.5, (i * 5) % 60 + 1, i % 4 == 0 ? 1 : 0));
        }
        if (duplicates) sb.append("1001,27,pro,309.50,6,0\n1001,27,pro,309.50,6,0\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sales() {
        StringBuilder sb = new StringBuilder("month,region,units,revenue\n");
        String[] regions = {"North", "South", "East", "West"};
        for (int m = 1; m <= 12; m++)
            for (String r : regions)
                sb.append(String.format(java.util.Locale.ROOT, "2024-%02d,%s,%d,%.2f%n", m, r, 100 + m * 7, (100 + m * 7) * 24.99));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] iris() {
        String csv = "sepal_length,sepal_width,petal_length,petal_width,species\n"
            + "5.1,3.5,1.4,0.2,setosa\n4.9,3.0,1.4,0.2,setosa\n4.7,3.2,1.3,0.2,setosa\n"
            + "7.0,3.2,4.7,1.4,versicolor\n6.4,3.2,4.5,1.5,versicolor\n6.9,3.1,4.9,1.5,versicolor\n"
            + "6.3,3.3,6.0,2.5,virginica\n5.8,2.7,5.1,1.9,virginica\n7.1,3.0,5.9,2.1,virginica\n";
        return csv.getBytes(StandardCharsets.UTF_8);
    }
}
