package renate.kernels.commons;

import renate.model.Person;
import se.vti.roundtrips.single.RoundTrip;

public interface Features {
    public static Features from(Person syntheticPerson) {
        throw new UnsupportedOperationException("Features.from(RoundTrip) not implemented for this Features type.");
    }
    public static Features from(RoundTrip<?> rt) {
        throw new UnsupportedOperationException("Features.from(RoundTrip) not implemented for this Features type.");
    }
}



