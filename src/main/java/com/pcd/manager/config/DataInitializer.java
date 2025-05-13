package com.pcd.manager.config;

import com.pcd.manager.model.User;
import com.pcd.manager.model.Tool;
// import com.pcd.manager.model.Passdown; // No longer creating sample passdowns directly here
import com.pcd.manager.model.Location;
import com.pcd.manager.repository.UserRepository;
import com.pcd.manager.repository.ToolRepository;
// import com.pcd.manager.repository.PassdownRepository; // No longer creating sample passdowns directly here
import com.pcd.manager.repository.LocationRepository;
// import com.pcd.manager.repository.TrackTrendRepository; 
// import com.pcd.manager.model.TrackTrend; // No longer creating sample track trends here
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
// import java.time.LocalDateTime; // No longer needed for sample passdowns
import java.util.List;
import java.util.Random;
// import java.util.Arrays; // No longer needed for old sample tools
// import java.util.HashSet; // No longer needed for sample passdowns/tracktrends
// import java.util.HashMap; // No longer needed for sample passdowns
import java.util.Optional;
// import java.util.Collections; // No longer needed for sample track trends
import java.util.ArrayList;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private final UserRepository userRepository;
    private final ToolRepository toolRepository;
    // private final PassdownRepository passdownRepository; // Removed
    private final LocationRepository locationRepository;
    private final PasswordEncoder passwordEncoder;
    // private final Random random = new Random(); // No longer directly used in this revised version.
    // private final TrackTrendRepository trackTrendRepository; // Removed, as we don't init TrackTrends here anymore

    @Autowired
    public DataInitializer(
            UserRepository userRepository,
            ToolRepository toolRepository,
            // PassdownRepository passdownRepository, // Removed
            LocationRepository locationRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.toolRepository = toolRepository;
        // this.passdownRepository = passdownRepository; // Removed
        this.locationRepository = locationRepository;
        this.passwordEncoder = passwordEncoder;
        // this.trackTrendRepository = trackTrendRepository; // Removed
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            // Create default admin user if no users exist
            if (userRepository.count() == 0) {
                createDefaultAdmin();
                createTestUsers();
            }
            
            // Create tools from CSV if none exist
            if (toolRepository.count() == 0) {
                createHardcodedTools();
            }
            
            // Create specific user and assign to tool for testing
            // This should run after tools are potentially created from CSV
            createAndAssignDuaneSmith();
            
            // Removed TrackTrend and Passdown initialization
            // logger.info("Data initialization completed successfully.");

        } catch (Exception e) {
            logger.error("Error during data initialization: {}", e.getMessage(), e);
            // Log the error but allow the application to continue
        }
    }

    private void createDefaultAdmin() {
        User adminUser = new User();
        adminUser.setEmail("admin@pcd.com");
        adminUser.setPassword(passwordEncoder.encode("admin123"));
        adminUser.setName("Admin User");
        adminUser.setRole("ADMIN");
        adminUser.setActive(true);
        
        userRepository.save(adminUser);
        
        logger.info("Default admin user created: admin@pcd.com / admin123");
    }
    
    private void createTestUsers() {
        User techUser = new User();
        techUser.setEmail("tech@pcd.com");
        techUser.setPassword(passwordEncoder.encode("tech123"));
        techUser.setName("Tech User");
        techUser.setRole("TECHNICIAN");
        techUser.setActive(true);
        
        userRepository.save(techUser);
        
        logger.info("Test technician user created: tech@pcd.com / tech123");
    }
    
    private void createHardcodedTools() {
        Location f52Location = locationRepository.findByStateAndFab("Arizona", "52")
            .orElseGet(() -> {
                logger.warn("Location Arizona F52 not found. Creating it. LocationType will be null.");
                Location newF52 = new Location();
                newF52.setName("Fab 52 Arizona");
                newF52.setState("Arizona");
                newF52.setFab("52");
                logger.info("LocationType for new F52 location will be null as Location.LocationType enum needs to be defined with FAB.");
                newF52.setDefaultLocation(false);
                return locationRepository.save(newF52);
            });

        List<ToolData> toolDataList = getToolData();
        int toolsCreatedCount = 0;

        for (ToolData td : toolDataList) {
            Tool tool = new Tool();
            tool.setName(td.name);
            if (td.secondaryName != null && !td.secondaryName.isEmpty()) {
                tool.setSecondaryName(td.secondaryName);
            }
            if (td.type != null && !td.type.isEmpty()) {
                try {
                    tool.setToolType(Tool.ToolType.valueOf(td.type.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid tool type '{}' for tool {}. Setting to null.", td.type, td.name);
                }
            }
            if (td.model1 != null && !td.model1.isEmpty()) {
                tool.setModel1(td.model1);
            }
            if (td.model2 != null && !td.model2.isEmpty()) {
                tool.setModel2(td.model2);
            }
            if (td.serialNumber1 != null && !td.serialNumber1.isEmpty()) {
                tool.setSerialNumber1(td.serialNumber1);
            }
            if (td.serialNumber2 != null && !td.serialNumber2.isEmpty()) {
                tool.setSerialNumber2(td.serialNumber2);
            }

            tool.setLocation(f52Location); 
            tool.setStatus(Tool.ToolStatus.NOT_STARTED);
            tool.setSetDate(LocalDate.now());
            
            toolRepository.save(tool);
            toolsCreatedCount++;
        }

        if (toolsCreatedCount > 0) {
            logger.info("Successfully created {} hardcoded tools and assigned to Location: {}", toolsCreatedCount, (f52Location != null ? f52Location.getDisplayName() : "N/A"));
        } else {
            logger.info("No hardcoded tools were created.");
        }
    }

    // Helper class for tool data
    private static class ToolData {
        String name, secondaryName, type, model1, model2, serialNumber1, serialNumber2;
        public ToolData(String name, String sn, String t, String m1, String m2, String s1, String s2) {
            this.name = name; this.secondaryName = sn; this.type = t; 
            this.model1 = m1; this.model2 = m2; 
            this.serialNumber1 = s1; this.serialNumber2 = s2;
        }
    }

    private List<ToolData> getToolData() {
        List<ToolData> list = new ArrayList<>();
        // Manually transcribed from CSV data
        // Header: Name,Secondary_Name,Type,Model1,Model,Serial_Number1,Serial_Number2
        list.add(new ToolData("RAK151D", null, "Slurry", "2312625", "2309011", "200465017-4", "200465044-47"));
        list.add(new ToolData("RR151D", null, "Slurry", "2313328", "2309011", "200465022-3", "200455776-29"));
        list.add(new ToolData("RAK152D", null, "Slurry", "2313194", null, "200466053-1", null));
        list.add(new ToolData("WK151D", null, "Slurry", "2313326", "2309011", "200463297-1", "200463298-40"));
        list.add(new ToolData("KT151D", "CH151D/RR152D", "Slurry", "2312622", "2309011", "200463695-2", "200455778-30"));
        list.add(new ToolData("FU151D", "CONT151D", "Slurry", "2309011", null, "200466036-51", null));
        list.add(new ToolData("EK151D", "KB152D", "Slurry", "2313193", null, "200466052-1", null));
        list.add(new ToolData("JP151D", "CH152D", "Slurry", "2313191", null, "200466050-1", null));
        list.add(new ToolData("JK151D", null, "Slurry", "2313325", "2309011", "200463160-1", "200463161-37"));
        list.add(new ToolData("WL151D", null, "Slurry", "2315214", "2309011", "200464053-1", "200463381-41"));
        list.add(new ToolData("HG151D", null, "Slurry", "2313556", "2309011", "200465024-2", "200465023-46"));
        list.add(new ToolData("HG152D", "FA151D", "Slurry", "2313556", "2309011", "200466493-4", "200455775-28"));
        list.add(new ToolData("RE151D", null, "Slurry", "2313330", "2309011", "200463698-2", "200463697-43"));
        list.add(new ToolData("EF151D", null, "Slurry", "2313329", "2309011", "200463290-1", "200463291-38"));
        list.add(new ToolData("VC151D", null, "Slurry", "2312620", "2309011", "200462472-2", "200455801-31"));
        list.add(new ToolData("TM151D", null, "Slurry", "2312617", "2309011", "200460354-2", "200455771-24"));
        list.add(new ToolData("XJ151D", "AE151D", "Slurry", "2313557", "2309011", "200466049-4", "200466048-52"));
        list.add(new ToolData("WC151D", null, "Slurry", "2312624", "2309011", "200465018-2", "200465019-44"));
        list.add(new ToolData("JS151D", null, "Slurry", "2312618", "2309011", "200460356-2", "200455773-26"));
        list.add(new ToolData("RFT151D", null, "Slurry", "2310218", "2309011", "200460355-3", "200455772-25"));
        list.add(new ToolData("RFT152D", null, "Slurry", "2310218", "2309011", "200466533-4", "200469736-64"));
        list.add(new ToolData("FB151D", null, "Slurry", "2312621", "2309011", "200462473-3", "200462474-36"));
        list.add(new ToolData("WR151D", "FB152D", "Slurry", "2312621", "2309011", "200465021-4", "200465020-45"));
        list.add(new ToolData("RZ151D", null, "Slurry", "2312619", "2309011", "200460357-2", "200455774-27"));
        list.add(new ToolData("AH151", "KOR154", "Chemblend", "2313196", null, "200466047-2", null));
        list.add(new ToolData("AR151", "BT154", "Chemblend", "2309019", null, "200466037-30", null));
        list.add(new ToolData("BH151", null, "Chemblend", "2310219", "2309019", "200460944-3", "200461418-22"));
        list.add(new ToolData("BH152", null, "Chemblend", "2313195", null, "200466055-2", null));
        list.add(new ToolData("BT151", null, "Chemblend", "2309019", "2310216", "200461416-20", "200460358-4"));
        list.add(new ToolData("BT152", null, "Chemblend", "2310216", "2309019", "200462009-5", "200461417-21"));
        list.add(new ToolData("GR151", "SSC152", "Chemblend", "2315220", "2309019", "200466046-3", "200466043-32"));
        list.add(new ToolData("HD151", null, "Chemblend", "2310216", "2309019", "200464063-8", "200463299-26"));
        list.add(new ToolData("HD152", null, "Chemblend", "2310216", "2309019", "200464086-9", "200463300-27"));
        list.add(new ToolData("JT151", null, "Chemblend", "2313327", "2309019", "200466038-2", "200465975-29"));
        list.add(new ToolData("KF151", null, "Chemblend", "2315219", "2309019", "200464087-4", "200463301-28"));
        list.add(new ToolData("KF152", null, "Chemblend", "2315219", "2309019", "200464085-3", "200466044-33"));
        list.add(new ToolData("KG151", null, "Chemblend", "2312626", "2309019", "200462476-2", "200462477-25"));
        list.add(new ToolData("KG152", null, "Chemblend", "2309019", "2312626", "200466042-31", "200466045-3"));
        list.add(new ToolData("KOR151", null, "Chemblend", "2310217", "2309019", "200460945-4", "200455777-19"));
        list.add(new ToolData("KOR152", null, "Chemblend", "2310217", "2309019", "200460946-5", "200461419-23"));
        list.add(new ToolData("SSC151", null, "Chemblend", "2315220", "2309019", "200463971-1", "200462471-24"));
        return list;
    }
    
    private void createAndAssignDuaneSmith() {
        Location arizonaF52 = locationRepository.findByStateAndFab("Arizona", "52").orElse(null); 
                
        if (arizonaF52 == null) {
            logger.warn("Duane Smith assignment: Location Arizona F52 not found. This location should ideally be created by createHardcodedTools if missing.");
        }
        
        Tool rr151d = toolRepository.findByName("RR151D").orElse(null); 

        if (rr151d == null) {
            logger.warn("Tool RR151D not found after hardcoded tool creation. Duane Smith cannot be assigned to it or have it as active tool.");
        }

        Optional<User> existingUser = userRepository.findByEmailIgnoreCase("duane.smith@emdgroup.com");

        User duaneSmithUser;
        if (existingUser.isEmpty()) {
            duaneSmithUser = new User();
            duaneSmithUser.setEmail("duane.smith@emdgroup.com");
            duaneSmithUser.setPassword(passwordEncoder.encode("password123")); 
            duaneSmithUser.setName("Duane Smith");
            duaneSmithUser.setRole("TECHNICIAN");
            duaneSmithUser.setActive(true);
            if (arizonaF52 != null) duaneSmithUser.setActiveSite(arizonaF52);
            userRepository.save(duaneSmithUser);
            logger.info("Created user: Duane Smith (duane.smith@emdgroup.com)");
        } else {
            duaneSmithUser = existingUser.get();
            logger.info("User Duane Smith already exists. Ensuring active site/tool are up-to-date.");
        }

        boolean userNeedsUpdate = false;
        if (arizonaF52 != null && !arizonaF52.equals(duaneSmithUser.getActiveSite())) {
            duaneSmithUser.setActiveSite(arizonaF52);
            userNeedsUpdate = true;
        }
        if (rr151d != null && !rr151d.equals(duaneSmithUser.getActiveTool())) {
            duaneSmithUser.setActiveTool(rr151d);
            userNeedsUpdate = true;
        } else if (rr151d == null && duaneSmithUser.getActiveTool() != null) { 
            duaneSmithUser.setActiveTool(null);
            userNeedsUpdate = true;
        }

        if (userNeedsUpdate) {
            userRepository.save(duaneSmithUser);
            logger.info("Updated active site/tool for Duane Smith.");
        }

        if (rr151d != null) {
            boolean alreadyAssigned = rr151d.getCurrentTechnicians().stream()
                                          .anyMatch(tech -> tech.getId().equals(duaneSmithUser.getId()));
            if (!alreadyAssigned) {
                 rr151d.getCurrentTechnicians().add(duaneSmithUser); 
                 toolRepository.save(rr151d); 
                 logger.info("Added/Ensured Duane Smith is in Tool RR151D's technician list.");
            } else {
                 logger.info("Duane Smith is already in Tool RR151D's technician list.");
            }
        } else {
             logger.info("Tool RR151D not found, cannot update its technician list with Duane Smith.");
        }
    }
} 