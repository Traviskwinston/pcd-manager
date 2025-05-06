package com.pcd.manager.config;

import com.pcd.manager.model.User;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.Location;
import com.pcd.manager.repository.UserRepository;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.repository.PassdownRepository;
import com.pcd.manager.repository.LocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ToolRepository toolRepository;
    private final PassdownRepository passdownRepository;
    private final LocationRepository locationRepository;
    private final PasswordEncoder passwordEncoder;
    private final Random random = new Random();

    @Autowired
    public DataInitializer(
            UserRepository userRepository,
            ToolRepository toolRepository,
            PassdownRepository passdownRepository,
            LocationRepository locationRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.toolRepository = toolRepository;
        this.passdownRepository = passdownRepository;
        this.locationRepository = locationRepository;
        this.passwordEncoder = passwordEncoder;
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
            
            // Create some sample tools if none exist
            if (toolRepository.count() == 0) {
                createSampleTools();
            }
            
            // Create sample passdowns if none exist
            if (passdownRepository.count() == 0) {
                createSamplePassdowns();
            }

            // Create specific user and assign to tool for testing
            createAndAssignDuaneSmith();
            
        } catch (Exception e) {
            System.err.println("Error during data initialization: " + e.getMessage());
            System.err.println("This may be caused by database already in use. The application will continue without initializing sample data.");
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
        
        System.out.println("Default admin user created: admin@pcd.com / admin123");
    }
    
    private void createTestUsers() {
        User techUser = new User();
        techUser.setEmail("tech@pcd.com");
        techUser.setPassword(passwordEncoder.encode("tech123"));
        techUser.setName("Tech User");
        techUser.setRole("TECHNICIAN");
        techUser.setActive(true);
        
        userRepository.save(techUser);
        
        System.out.println("Test technician user created: tech@pcd.com / tech123");
    }
    
    private void createSampleTools() {
        List<Location> locations = locationRepository.findAll();
        if (locations.isEmpty()) {
            System.out.println("Cannot create sample tools: no locations found in database");
            return;
        }
        // Default tool names
        List<String> defaultNames = Arrays.asList(
            "RZ151D","BH151D","KOR151D","SSC151D","KG151D","TM151D","JS151D","FB151D","EF151D",
            "KOR152D","AH151D","HG151D","FU151D","JT151D","KF151D","WL151D","GR151D","WC151D","HD151D",
            "RE151D","WK151D","JP151D","RAK151D","RR151D","JK151D","BT151D","XJ151D","EK151D","BH152D",
            "BT152D","MHR151D","AR151D","WL152D","HG152D","HD152D","KG152D","FB152D","BT153D","BT154D",
            "RAK152D","KF152D","RFT151D","RR152D","WL153D","KOR153D"
        );
        for (String name : defaultNames) {
            Tool tool = new Tool();
            tool.setName(name);
            if (random.nextBoolean()) {
                char suffixChar = (char) ('A' + random.nextInt(26));
                tool.setSecondaryName(name + suffixChar);
            }
            // Random type
            Tool.ToolType[] types = Tool.ToolType.values();
            tool.setToolType(types[random.nextInt(types.length)]);
            // Primary model (7 digits starting with 23)
            String model1 = String.valueOf(random.nextInt(100000) + 2300000);
            tool.setModel1(model1);
            // Primary serial (200 + 6 digits) + '-' + 1-2 digit
            String serial1 = "200" + String.format("%06d", random.nextInt(1000000))
                + "-" + (random.nextInt(98) + 1);
            tool.setSerialNumber1(serial1);
            // 50% chance of secondary model & serial
            if (random.nextBoolean()) {
                String model2 = String.valueOf(random.nextInt(100000) + 2300000);
                tool.setModel2(model2);
                String serial2 = "200" + String.format("%06d", random.nextInt(1000000))
                    + "-" + (random.nextInt(98) + 1);
                tool.setSerialNumber2(serial2);
            }
            tool.setLocation(getRandomLocation(locations));
            tool.setStatus(Tool.ToolStatus.NOT_STARTED);
            tool.setSetDate(LocalDate.now());
            toolRepository.save(tool);
        }
        System.out.println("Created " + defaultNames.size() + " sample tools");
    }
    
    private void createSamplePassdowns() {
        List<User> users = userRepository.findAll();
        List<Tool> tools = toolRepository.findAll();
        
        if (users.isEmpty() || tools.isEmpty()) {
            System.out.println("Cannot create sample passdowns: users or tools not found in database");
            return;
        }
        
        // Sample passdown comments
        String[] passdownComments = {
            "Completed initial setup of the tool. All systems operational and ready for testing.",
            "Changed filters and cleaned mixing chamber. Performance has improved significantly.",
            "Encountered issue with the control panel. Error code E-223 appears intermittently. Need to check wiring connections.",
            "Software update successful. New features are now available in the monitoring dashboard.",
            "Calibrated sensors according to specification. Readings are now within acceptable range.",
            "Replaced worn pump seals. No more leaking observed during high-pressure operations.",
            "Daily maintenance completed. All fluid levels topped off and system diagnostics passed.",
            "Fixed power supply issue. The intermittent shutdowns should no longer occur.",
            "Training session conducted for new operators. Everyone is now familiar with the safety procedures.",
            "Performed thorough cleaning of all components. Tool is running quieter and more efficiently now."
        };
        
        // Create 15 sample passdowns
        for (int i = 0; i < 15; i++) {
            Passdown passdown = new Passdown();
            passdown.setComment(passdownComments[random.nextInt(passdownComments.length)]);
            passdown.setDate(LocalDate.now().minusDays(random.nextInt(30)));
            passdown.setCreatedDate(LocalDateTime.now().minusDays(random.nextInt(30)).minusHours(random.nextInt(24)));
            passdown.setUser(users.get(random.nextInt(users.size())));
            
            // 80% chance to have a tool associated
            if (random.nextDouble() < 0.8) {
                passdown.setTool(tools.get(random.nextInt(tools.size())));
            }
            
            // Initialize empty collections to ensure they're not null
            passdown.setPicturePaths(new HashSet<>());
            passdown.setPictureNames(new HashMap<>());
            
            passdownRepository.save(passdown);
        }
        
        System.out.println("Created 15 sample passdowns with proper picture collections initialization");
    }
    
    private Location getRandomLocation(List<Location> locations) {
        return locations.get(random.nextInt(locations.size()));
    }
    
    private void createAndAssignDuaneSmith() {
        Location arizonaF52 = locationRepository.findByStateAndFab("Arizona", "52")
                .orElse(null); // Handle case where AZ F52 might not exist yet
                
        Tool rr151d = toolRepository.findByName("RR151D")
                .orElse(null); // Handle case where RR151D might not exist yet

        if (arizonaF52 == null) {
            System.out.println("Skipping Duane Smith creation: Location Arizona F52 not found.");
            return;
        }
        
        if (rr151d == null) {
            System.out.println("Skipping Duane Smith assignment: Tool RR151D not found.");
            // Proceed to create the user but cannot assign the tool or set active tool
        }

        Optional<User> existingUser = userRepository.findByEmail("duane.smith@emdgroup.com");

        if (existingUser.isEmpty()) {
            User duane = new User();
            duane.setEmail("duane.smith@emdgroup.com");
            duane.setPassword(passwordEncoder.encode("password"));
            duane.setName("Duane Smith");
            duane.setRole("TECHNICIAN");
            duane.setActive(true);
            duane.setActiveSite(arizonaF52);
            if (rr151d != null) {
                 duane.setActiveTool(rr151d);
            }
           
            User savedDuane = userRepository.save(duane);
            System.out.println("Created user: Duane Smith (duane.smith@emdgroup.com)");

            // Now assign the newly saved user to the tool if the tool exists
            if (rr151d != null) {
                rr151d.getCurrentTechnicians().add(savedDuane);
                toolRepository.save(rr151d);
                System.out.println("Assigned Duane Smith to Tool RR151D");
            }
        } else {
            System.out.println("User Duane Smith already exists. Checking assignment...");
            // User exists, ensure assignment is made if tool exists
            if (rr151d != null && !rr151d.getCurrentTechnicians().contains(existingUser.get())) {
                 rr151d.getCurrentTechnicians().add(existingUser.get());
                 toolRepository.save(rr151d);
                 System.out.println("Assigned existing Duane Smith to Tool RR151D");
            }
            // Optionally update active site/tool if needed
            User duane = existingUser.get();
            boolean needsUpdate = false;
            if (!arizonaF52.equals(duane.getActiveSite())) {
                duane.setActiveSite(arizonaF52);
                needsUpdate = true;
            }
            if (rr151d != null && !rr151d.equals(duane.getActiveTool())) {
                duane.setActiveTool(rr151d);
                needsUpdate = true;
            }
            if (needsUpdate) {
                userRepository.save(duane);
                System.out.println("Updated active site/tool for existing Duane Smith");
            }
        }
    }
} 