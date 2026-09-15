package research;

import java.util.List;

/** min/max/mean/median/spread over a list of doubles — never a physical-hardware claim, just arithmetic. */
public record Stats(double min, double max, double mean, double median, double spread) {
    public static Stats of(List<Double> values) {
        if (values.isEmpty()) throw new IllegalArgumentException("cannot compute stats over zero values");
        List<Double> sorted = values.stream().sorted().toList();
        double min = sorted.get(0);
        double max = sorted.get(sorted.size() - 1);
        double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        int n = sorted.size();
        double median = n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        return new Stats(min, max, mean, median, max - min);
    }

    public String toJson() {
        return "{\"min\":" + min + ",\"max\":" + max + ",\"mean\":" + mean + ",\"median\":" + median + ",\"spread\":" + spread + "}";
    }
}
