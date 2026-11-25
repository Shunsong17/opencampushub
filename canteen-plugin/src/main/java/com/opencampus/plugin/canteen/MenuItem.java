package com.opencampus.plugin.canteen;

import java.math.BigDecimal;
import java.time.LocalDate;

record MenuItem(String id, String name, String canteen, LocalDate date, String meal, BigDecimal price, String window) {
}
