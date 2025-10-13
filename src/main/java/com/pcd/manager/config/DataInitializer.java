package com.pcd.manager.config;

import com.pcd.manager.model.User;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.Location;
import com.pcd.manager.model.MapGridItem;
import com.pcd.manager.repository.UserRepository;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.repository.LocationRepository;
import com.pcd.manager.repository.MapGridItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private final UserRepository userRepository;
    private final ToolRepository toolRepository;
    private final LocationRepository locationRepository;
    private final PasswordEncoder passwordEncoder;
    private final MapGridItemRepository mapGridItemRepository;

    @Autowired
    public DataInitializer(
            UserRepository userRepository,
            ToolRepository toolRepository,
            LocationRepository locationRepository,
            PasswordEncoder passwordEncoder,
            MapGridItemRepository mapGridItemRepository) {
        this.userRepository = userRepository;
        this.toolRepository = toolRepository;
        this.locationRepository = locationRepository;
        this.passwordEncoder = passwordEncoder;
        this.mapGridItemRepository = mapGridItemRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            // Create users if none exist
            if (userRepository.count() == 0) {
                createDefaultAdmin();
                createTestUsers();
                createSpecificUsers();
            } else {
                // Update existing users if needed
                updateExistingUsers();
            }
            
            // Create tools from CSV if none exist
            if (toolRepository.count() == 0) {
                createHardcodedTools();
            }
            
            // Create specific user and assign to tool for testing
            // This should run after tools are potentially created from CSV
            createAndAssignDuaneSmith();
            
            // Create facility grid layout
            createFacilityGridLayout();
            
            logger.info("Data initialization completed successfully.");

        } catch (Exception e) {
            logger.error("Error during data initialization: {}", e.getMessage(), e);
            // Log the error but allow the application to continue
        }
    }

    private void createDefaultAdmin() {
        User adminUser = new User();
        adminUser.setEmail("admin@pcd.com");
        adminUser.setPassword(passwordEncoder.encode("5Z2eyQfc!"));
        adminUser.setName("Admin User");
        adminUser.setRole("ADMIN");
        adminUser.setActive(true);
        
        // Set default location as active site if available
        Optional<Location> defaultLocation = locationRepository.findByDefaultLocationIsTrue();
        defaultLocation.ifPresent(adminUser::setActiveSite);
        
        userRepository.save(adminUser);
        
        logger.info("Default admin user created: admin@pcd.com / [updated password]");
    }
    
    private void createTestUsers() {
        User techUser = new User();
        techUser.setEmail("tech@pcd.com");
        techUser.setPassword(passwordEncoder.encode("tech123"));
        techUser.setName("Tech User");
        techUser.setRole("TECHNICIAN");
        techUser.setActive(true);
        
        // Set default location as active site if available
        Optional<Location> defaultLocation = locationRepository.findByDefaultLocationIsTrue();
        defaultLocation.ifPresent(techUser::setActiveSite);
        
        userRepository.save(techUser);
        
        logger.info("Test technician user created: tech@pcd.com / tech123");
    }
    
    private void createSpecificUsers() {
        // Create Travis Winston (Admin)
        User travisUser = new User();
        travisUser.setEmail("Travis.Winston@emdgroup.com");
        travisUser.setPassword(passwordEncoder.encode("5Z2eyQfc!"));
        travisUser.setName("Travis Winston");
        travisUser.setRole("ADMIN");
        travisUser.setActive(true);
        
        // Set default location as active site if available
        Optional<Location> defaultLocation = locationRepository.findByDefaultLocationIsTrue();
        defaultLocation.ifPresent(travisUser::setActiveSite);
        
        userRepository.save(travisUser);
        logger.info("Travis Winston user created: Travis.Winston@emdgroup.com");
        
        // Create Casey James (Technician)
        User caseyUser = new User();
        caseyUser.setEmail("Casey.James@emdgroup.com");
        caseyUser.setPassword(passwordEncoder.encode("emdpassword1"));
        caseyUser.setName("Casey James");
        caseyUser.setRole("TECHNICIAN");
        caseyUser.setActive(true);
        
        defaultLocation.ifPresent(caseyUser::setActiveSite);
        
        userRepository.save(caseyUser);
        logger.info("Casey James user created: Casey.James@emdgroup.com");
        
        // Create Guest user
        User guestUser = new User();
        guestUser.setEmail("Guest@pcdmanager.com");
        guestUser.setPassword(passwordEncoder.encode("emdpassword1"));
        guestUser.setName("Guest User");
        guestUser.setRole("TECHNICIAN");
        guestUser.setActive(true);
        
        defaultLocation.ifPresent(guestUser::setActiveSite);
        
        userRepository.save(guestUser);
        logger.info("Guest user created: Guest@pcdmanager.com");
        
        // Create Aaron Balliett user (AB initials)
        User aaron = new User();
        aaron.setEmail("Aaron.Balliett@emdgroup.com");
        aaron.setPassword(passwordEncoder.encode("emdpassword1"));
        aaron.setName("Aaron Balliett");
        aaron.setRole("TECHNICIAN");
        aaron.setActive(true);
        defaultLocation.ifPresent(aaron::setActiveSite);
        userRepository.save(aaron);
        logger.info("Aaron Balliett user created: Aaron.Balliett@emdgroup.com (Initials: AB)");
        
        // Create Jose Martinez user (JM initials)
        User jose = new User();
        jose.setEmail("Jose.Martinez@emdgroup.com");
        jose.setPassword(passwordEncoder.encode("emdpassword1"));
        jose.setName("Jose Martinez");
        jose.setRole("TECHNICIAN");
        jose.setActive(true);
        defaultLocation.ifPresent(jose::setActiveSite);
        userRepository.save(jose);
        logger.info("Jose Martinez user created: Jose.Martinez@emdgroup.com (Initials: JM)");
        
        // Create Raquel Dee user (RD initials)
        User raquel = new User();
        raquel.setEmail("Raquel.Dee@emdgroup.com");
        raquel.setPassword(passwordEncoder.encode("emdpassword1"));
        raquel.setName("Raquel Dee");
        raquel.setRole("TECHNICIAN");
        raquel.setActive(true);
        defaultLocation.ifPresent(raquel::setActiveSite);
        userRepository.save(raquel);
        logger.info("Raquel Dee user created: Raquel.Dee@emdgroup.com (Initials: RD)");
        
        // Create Erasto Campo user (EC initials)
        User erasto = new User();
        erasto.setEmail("Erasto.Campo@emdgroup.com");
        erasto.setPassword(passwordEncoder.encode("emdpassword1"));
        erasto.setName("Erasto Campo");
        erasto.setRole("TECHNICIAN");
        erasto.setActive(true);
        defaultLocation.ifPresent(erasto::setActiveSite);
        userRepository.save(erasto);
        logger.info("Erasto Campo user created: Erasto.Campo@emdgroup.com (Initials: EC)");
    }
    
    private void updateExistingUsers() {
        // Fix admin password if it exists
        Optional<User> adminUser = userRepository.findByEmailIgnoreCase("admin@pcd.com");
        if (adminUser.isPresent()) {
            User admin = adminUser.get();
            admin.setPassword(passwordEncoder.encode("5Z2eyQfc!"));
            userRepository.save(admin);
            logger.info("Reset admin password to new configured value");
        }
        
        // Update Duane Smith password if he exists
        Optional<User> duaneUser = userRepository.findByEmailIgnoreCase("duane.smith@emdgroup.com");
        if (duaneUser.isPresent()) {
            User duane = duaneUser.get();
            duane.setPassword(passwordEncoder.encode("emdpassword1"));
            userRepository.save(duane);
            logger.info("Updated Duane Smith password");
        }
        
        // Ensure Travis exists and is admin
        Optional<User> travisUser = userRepository.findByEmailIgnoreCase("Travis.Winston@emdgroup.com");
        if (travisUser.isEmpty()) {
            User travis = new User();
            travis.setEmail("Travis.Winston@emdgroup.com");
            travis.setPassword(passwordEncoder.encode("5Z2eyQfc!"));
            travis.setName("Travis Winston");
            travis.setRole("ADMIN");
            travis.setActive(true);
            
            Optional<Location> defaultLocation = locationRepository.findByDefaultLocationIsTrue();
            defaultLocation.ifPresent(travis::setActiveSite);
            
            userRepository.save(travis);
            logger.info("Created Travis Winston user: Travis.Winston@emdgroup.com");
        } else {
            User travis = travisUser.get();
            travis.setRole("ADMIN");
            userRepository.save(travis);
            logger.info("Updated Travis Winston role to ADMIN");
        }
        
        // Ensure Casey exists
        Optional<User> caseyUser = userRepository.findByEmailIgnoreCase("Casey.James@emdgroup.com");
        if (caseyUser.isEmpty()) {
            User casey = new User();
            casey.setEmail("Casey.James@emdgroup.com");
            casey.setPassword(passwordEncoder.encode("emdpassword1"));
            casey.setName("Casey James");
            casey.setRole("TECHNICIAN");
            casey.setActive(true);
            
            Optional<Location> defaultLocation = locationRepository.findByDefaultLocationIsTrue();
            defaultLocation.ifPresent(casey::setActiveSite);
            
            userRepository.save(casey);
            logger.info("Created Casey James user: Casey.James@emdgroup.com");
        }
        
        // Ensure Guest exists
        Optional<User> guestUser = userRepository.findByEmailIgnoreCase("Guest@pcdmanager.com");
        if (guestUser.isEmpty()) {
            User guest = new User();
            guest.setEmail("Guest@pcdmanager.com");
            guest.setPassword(passwordEncoder.encode("emdpassword1"));
            guest.setName("Guest User");
            guest.setRole("TECHNICIAN");
            guest.setActive(true);
            
            Optional<Location> defaultLocation = locationRepository.findByDefaultLocationIsTrue();
            defaultLocation.ifPresent(guest::setActiveSite);
            
            userRepository.save(guest);
            logger.info("Created Guest user: Guest@pcdmanager.com");
        }
        
        // Ensure Aaron Balliett exists (AB initials)
        Optional<User> aaronUser = userRepository.findByEmailIgnoreCase("Aaron.Balliett@emdgroup.com");
        if (aaronUser.isEmpty()) {
            User aaron = new User();
            aaron.setEmail("Aaron.Balliett@emdgroup.com");
            aaron.setPassword(passwordEncoder.encode("emdpassword1"));
            aaron.setName("Aaron Balliett");
            aaron.setRole("TECHNICIAN");
            aaron.setActive(true);
            
            Optional<Location> defaultLocation = locationRepository.findByDefaultLocationIsTrue();
            defaultLocation.ifPresent(aaron::setActiveSite);
            
            userRepository.save(aaron);
            logger.info("Created Aaron Balliett user: Aaron.Balliett@emdgroup.com (Initials: AB)");
        }
        
        // Ensure Jose Martinez exists (JM initials)
        Optional<User> joseUser = userRepository.findByEmailIgnoreCase("Jose.Martinez@emdgroup.com");
        if (joseUser.isEmpty()) {
            User jose = new User();
            jose.setEmail("Jose.Martinez@emdgroup.com");
            jose.setPassword(passwordEncoder.encode("emdpassword1"));
            jose.setName("Jose Martinez");
            jose.setRole("TECHNICIAN");
            jose.setActive(true);
            
            Optional<Location> defaultLocation = locationRepository.findByDefaultLocationIsTrue();
            defaultLocation.ifPresent(jose::setActiveSite);
            
            userRepository.save(jose);
            logger.info("Created Jose Martinez user: Jose.Martinez@emdgroup.com (Initials: JM)");
        }
        
        // Ensure Raquel Dee exists (RD initials)
        Optional<User> raquelUser = userRepository.findByEmailIgnoreCase("Raquel.Dee@emdgroup.com");
        if (raquelUser.isEmpty()) {
            User raquel = new User();
            raquel.setEmail("Raquel.Dee@emdgroup.com");
            raquel.setPassword(passwordEncoder.encode("emdpassword1"));
            raquel.setName("Raquel Dee");
            raquel.setRole("TECHNICIAN");
            raquel.setActive(true);
            
            Optional<Location> defaultLocation = locationRepository.findByDefaultLocationIsTrue();
            defaultLocation.ifPresent(raquel::setActiveSite);
            
            userRepository.save(raquel);
            logger.info("Created Raquel Dee user: Raquel.Dee@emdgroup.com (Initials: RD)");
        }
        
        // Ensure Erasto Campo exists (EC initials)
        Optional<User> erastoUser = userRepository.findByEmailIgnoreCase("Erasto.Campo@emdgroup.com");
        if (erastoUser.isEmpty()) {
            User erasto = new User();
            erasto.setEmail("Erasto.Campo@emdgroup.com");
            erasto.setPassword(passwordEncoder.encode("emdpassword1"));
            erasto.setName("Erasto Campo");
            erasto.setRole("TECHNICIAN");
            erasto.setActive(true);
            
            Optional<Location> defaultLocation = locationRepository.findByDefaultLocationIsTrue();
            defaultLocation.ifPresent(erasto::setActiveSite);
            
            userRepository.save(erasto);
            logger.info("Created Erasto Campo user: Erasto.Campo@emdgroup.com (Initials: EC)");
        }
    }
    
    private void createHardcodedTools() {
        Location f52Location = locationRepository.findByStateAndFab("Arizona", "52")
            .orElseGet(() -> {
                logger.warn("Location Arizona F52 not found. Creating it. LocationType will be null.");
                Location newF52 = new Location();
                newF52.setName("Fab 52 Arizona");
                newF52.setState("Arizona");
                newF52.setFab("52");
                newF52.setDisplayName("AZ F52");
                newF52.setTimeZone("America/Phoenix"); // Arizona doesn't observe DST
                newF52.setAddress("2850 E Broadway Rd, Tempe, AZ 85282");
                newF52.setDefaultLocation(false);
                
                // Set default customer info
                newF52.setCustomerName("EMD Electronics");
                newF52.setCustomerPhone("(480) 555-0100");
                newF52.setCustomerEmail("facilities@emdelectronics.com");
                
                logger.info("LocationType for new F52 location will be null as Location.LocationType enum needs to be defined with FAB.");
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

            tool.setLocationName("AZ F52"); 
            
            // Set specific tool statuses based on user requirements
            if ("RAK152D".equals(td.name)) {
                tool.setStatus(Tool.ToolStatus.NOT_STARTED);
            } else if ("HD152D".equals(td.name)) {
                tool.setStatus(Tool.ToolStatus.IN_PROGRESS);
            } else if ("RR152".equals(td.name) || "RR152D".equals(td.name)) {
                tool.setStatus(Tool.ToolStatus.IN_PROGRESS);
            } else {
                // All other tools set to COMPLETED
                tool.setStatus(Tool.ToolStatus.COMPLETED);
            }
            
            tool.setSetDate(LocalDate.now());
            
            toolRepository.save(tool);
            toolsCreatedCount++;
        }

        // Removed GasGuard seed data per requirements

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
        list.add(new ToolData("HG152D", "FA151D/VB151D", "Slurry", "2313556", "2309011", "200466493-4", "200455775-28"));
        list.add(new ToolData("RE151D", null, "Slurry", "2313330", "2309011", "200463698-2", "200463697-43"));
        list.add(new ToolData("EF151D", null, "Slurry", "2313329", "2309011", "200463290-1", "200463291-38"));
        list.add(new ToolData("VC151D", null, "Slurry", "2312620", "2309011", "200462472-2", "200455801-31"));
        list.add(new ToolData("TM151D", null, "Slurry", "2312617", "2309011", "200460354-2", "200455771-24"));
        list.add(new ToolData("XJ151D", "AE151D", "Slurry", "2313557", "2309011", "200466049-4", "200466048-52"));
        list.add(new ToolData("WC151D", "KB151D", "Slurry", "2312624", "2309011", "200465018-2", "200465019-44"));
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
            duaneSmithUser.setPassword(passwordEncoder.encode("emdpassword1")); 
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
    
    private void createFacilityGridLayout() {
        logger.info("Creating facility grid layout...");
        
        // Check if grid items already exist
        if (mapGridItemRepository.count() > 0) {
            logger.info("Grid items already exist, skipping grid layout creation");
            return;
        }
        
        // Get the admin user to use as creator
        User adminUser = userRepository.findByEmailIgnoreCase("admin@pcd.com").orElse(null);
        if (adminUser == null) {
            logger.warn("Admin user not found, cannot create grid layout");
            return;
        }
        
        // Place tools on the grid
        createToolGridPlacements(adminUser);
        
        logger.info("Facility grid layout creation completed");
    }
    
    private void createToolGridPlacements(User adminUser) {
        // Exact layout with proper 2-grid spacing between tools (each "-" = 2 empty grid squares)
        
        // Column 1 (HD column): -, -, HD151, -, KF151, -, HD152, KF152(touching), -, AH151, GR151(touching)
        placeToolOnGrid(adminUser, "HD151", 3, 3, 4, 2);     // Row 3-4
        placeToolOnGrid(adminUser, "KF151", 3, 7, 4, 2);     // Row 7-8 (3+2+2=7, 2 gap)
        placeToolOnGrid(adminUser, "HD152", 3, 11, 4, 2);    // Row 11-12 (7+2+2=11, 2 gap)
        placeToolOnGrid(adminUser, "KF152", 3, 13, 4, 2);    // Row 13-14 (touching HD152)
        placeToolOnGrid(adminUser, "AH151", 3, 17, 4, 2);    // Row 17-18 (13+2+2=17, 2 gap)
        placeToolOnGrid(adminUser, "GR151", 3, 19, 4, 2);    // Row 19-20 (touching AH151)
        
        // Column 2 (BT column): -, -, BT151, -, BT152
        placeToolOnGrid(adminUser, "BT151", 9, 3, 4, 2);     // Row 3-4
        placeToolOnGrid(adminUser, "BT152", 9, 7, 4, 2);     // Row 7-8 (2 gap)
        
        // Column 3 (RAK column): -, RAK151D, -, RR151D, RAK152D(touching), -, WK151D, KT151D(touching), -, FU151D, EK151D(touching), -, JP151D
        placeToolOnGrid(adminUser, "RAK151D", 15, 2, 4, 2);  // Row 2-3 (1 higher than HD151)
        placeToolOnGrid(adminUser, "RR151D", 15, 6, 4, 2);   // Row 6-7 (2+2+2=6, 2 gap)
        placeToolOnGrid(adminUser, "RAK152D", 15, 8, 4, 2);  // Row 8-9 (touching RR151D)
        placeToolOnGrid(adminUser, "WK151D", 15, 12, 4, 2);  // Row 12-13 (8+2+2=12, 2 gap)
        placeToolOnGrid(adminUser, "KT151D", 15, 14, 4, 2);  // Row 14-15 (touching WK151D)
        placeToolOnGrid(adminUser, "FU151D", 15, 18, 4, 2);  // Row 18-19 (14+2+2=18, 2 gap)
        placeToolOnGrid(adminUser, "EK151D", 15, 20, 4, 2);  // Row 20-21 (touching FU151D)
        placeToolOnGrid(adminUser, "JP151D", 15, 24, 4, 2);  // Row 24-25 (20+2+2=24, 2 gap)
        
        // Column 4 (KOR column): -, -, KOR151, -, BH151, -, KOR152, -, BH152, JT151(touching), -, AR151
        placeToolOnGrid(adminUser, "KOR151", 21, 3, 4, 2);   // Row 3-4
        placeToolOnGrid(adminUser, "BH151", 21, 7, 4, 2);    // Row 7-8 (2 gap)
        placeToolOnGrid(adminUser, "KOR152", 21, 11, 4, 2);  // Row 11-12 (2 gap)
        placeToolOnGrid(adminUser, "BH152", 21, 15, 4, 2);   // Row 15-16 (2 gap)
        placeToolOnGrid(adminUser, "JT151", 21, 17, 4, 2);   // Row 17-18 (touching BH152)
        placeToolOnGrid(adminUser, "AR151", 21, 21, 4, 2);   // Row 21-22 (17+2+2=21, 2 gap)
        
        // Column 5 (KG column - touching column 4): -, -, KG151, -, SSC151, -, KG152
        placeToolOnGrid(adminUser, "KG151", 25, 3, 4, 2);    // Row 3-4 (touching KOR151)
        placeToolOnGrid(adminUser, "SSC151", 25, 7, 4, 2);   // Row 7-8 (touching BH151)
        placeToolOnGrid(adminUser, "KG152", 25, 11, 4, 2);   // Row 11-12 (touching KOR152)
        
        // Column 6 (JK column - 2 gap from column 5): -, JK151D, -, WL151D, HG151D(touching), -, HG152D, RE151D(touching)
        placeToolOnGrid(adminUser, "JK151D", 31, 2, 4, 2);   // Row 2-3 (1 higher than HD151)
        placeToolOnGrid(adminUser, "WL151D", 31, 6, 4, 2);   // Row 6-7 (2+2+2=6, 2 gap)
        placeToolOnGrid(adminUser, "HG151D", 31, 8, 4, 2);   // Row 8-9 (touching WL151D)
        placeToolOnGrid(adminUser, "HG152D", 31, 12, 4, 2);  // Row 12-13 (8+2+2=12, 2 gap)
        placeToolOnGrid(adminUser, "RE151D", 31, 14, 4, 2);  // Row 14-15 (touching HG152D)
        
        // Column 7 (EF column - touching column 6): -, EF151D, -, VC151D, TM151D(touching), -, XJ151D, WC151D(touching)
        placeToolOnGrid(adminUser, "EF151D", 35, 2, 4, 2);   // Row 2-3 (touching JK151D)
        placeToolOnGrid(adminUser, "VC151D", 35, 6, 4, 2);   // Row 6-7 (touching WL151D)
        placeToolOnGrid(adminUser, "TM151D", 35, 8, 4, 2);   // Row 8-9 (touching HG151D)
        placeToolOnGrid(adminUser, "XJ151D", 35, 12, 4, 2);  // Row 12-13 (touching HG152D)
        placeToolOnGrid(adminUser, "WC151D", 35, 14, 4, 2);  // Row 14-15 (touching RE151D)
        
        // Column 8 (JS column - gap from column 7): -, JS151D, RFT151D(touching), RFT152D(touching), FB151D(touching), WR151D(touching), RZ151D(touching)
        placeToolOnGrid(adminUser, "JS151D", 41, 2, 4, 2);   // Row 2-3 (1 higher than HD151)
        placeToolOnGrid(adminUser, "RFT151D", 41, 4, 4, 2);  // Row 4-5 (touching JS151D)
        placeToolOnGrid(adminUser, "RFT152D", 41, 6, 4, 2);  // Row 6-7 (touching RFT151D)
        placeToolOnGrid(adminUser, "FB151D", 41, 8, 4, 2);   // Row 8-9 (touching RFT152D)
        placeToolOnGrid(adminUser, "WR151D", 41, 10, 4, 2);  // Row 10-11 (touching FB151D)
        placeToolOnGrid(adminUser, "RZ151D", 41, 12, 4, 2);  // Row 12-13 (touching WR151D)
    }

    private void placeToolOnGrid(User creator, String toolName, int x, int y, int width, int height) {
        Tool tool = toolRepository.findByName(toolName).orElse(null);
        if (tool == null) {
            logger.debug("Tool {} not found in database, skipping grid placement", toolName);
            return;
        }
        
        // Get the default location for the grid item
        Location defaultLocation = locationRepository.findByDefaultLocationIsTrue()
            .orElseGet(() -> {
                // If no default location exists, try to find Arizona F52
                return locationRepository.findByStateAndFab("Arizona", "52")
                    .orElseGet(() -> {
                        // Create a default location if none exists
                        logger.warn("No default location found, creating Arizona F52 as default");
                        Location newLocation = new Location();
                        newLocation.setState("Arizona");
                        newLocation.setFab("52");
                        newLocation.setName("Fab 52 Arizona");
                        newLocation.setDisplayName("AZ F52");
                        newLocation.setTimeZone("America/Phoenix"); // Arizona doesn't observe DST
                        newLocation.setAddress("2850 E Broadway Rd, Tempe, AZ 85282");
                        newLocation.setDefaultLocation(true);
                        
                        // Set default customer info
                        newLocation.setCustomerName("EMD Electronics");
                        newLocation.setCustomerPhone("(480) 555-0100");
                        newLocation.setCustomerEmail("facilities@emdelectronics.com");
                        
                        return locationRepository.save(newLocation);
                    });
            });
        
        MapGridItem item = new MapGridItem();
        item.setType(MapGridItem.ItemType.TOOL);
        item.setTool(tool);
        item.setLocation(defaultLocation);  // Set the required location
        item.setX(x);
        item.setY(y);
        item.setWidth(width);
        item.setHeight(height);
        item.setCreatedBy(creator);
        item.setUpdatedBy(creator);
        mapGridItemRepository.save(item);
        
        logger.debug("Placed tool {} on grid at position ({}, {}) for location {}", 
                    toolName, x, y, defaultLocation.getDisplayName());
    }
}