package com.smartcourse.registration.config;

import com.smartcourse.registration.dao.RegistrationDao;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OfferingInitializer implements ApplicationRunner {

    private final RegistrationDao registrationDao;
    private final RegistrationProperties properties;

    public OfferingInitializer(
            RegistrationDao registrationDao,
            RegistrationProperties properties) {
        this.registrationDao = registrationDao;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (registrationDao.findOfferingForUpdate(properties.getOfferingId()).isEmpty()) {
            registrationDao.createOffering(
                    properties.getOfferingId(),
                    properties.getCapacity());
        }
    }
}
