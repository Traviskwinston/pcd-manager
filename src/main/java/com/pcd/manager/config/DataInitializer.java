package com.pcd.manager.config;

import com.pcd.manager.model.User;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.Location;
import com.pcd.manager.model.MapGridItem;
import com.pcd.manager.repository.UserRepository;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.repository.PassdownRepository;
import com.pcd.manager.repository.LocationRepository;
import com.pcd.manager.repository.MapGridItemRepository;
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
import java.time.LocalDateTime;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
// import java.util.Arrays; // No longer needed for old sample tools
// import java.util.HashSet; // No longer needed for sample passdowns/tracktrends
// import java.util.HashMap; // No longer needed for sample passdowns
import java.util.Optional;
// import java.util.Collections; // No longer needed for sample track trends
import java.util.ArrayList;
import jakarta.annotation.PostConstruct;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.MonthDay;
import com.pcd.manager.model.Rma;
import com.pcd.manager.model.RmaStatus;
import com.pcd.manager.model.RmaPriority;
import com.pcd.manager.model.RmaReasonForRequest;
import com.pcd.manager.model.PartLineItem;
import com.pcd.manager.repository.RmaRepository;
import com.pcd.manager.model.RmaComment;
import com.pcd.manager.repository.RmaCommentRepository;
import com.pcd.manager.model.TrackTrend;
import com.pcd.manager.model.TrackTrendComment;
import com.pcd.manager.model.ToolComment;
import com.pcd.manager.repository.TrackTrendRepository;
import com.pcd.manager.repository.TrackTrendCommentRepository;
import com.pcd.manager.repository.ToolCommentRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private final UserRepository userRepository;
    private final ToolRepository toolRepository;
    private final PassdownRepository passdownRepository;
    private final LocationRepository locationRepository;
    private final PasswordEncoder passwordEncoder;
    private final MapGridItemRepository mapGridItemRepository;
    private final RmaRepository rmaRepository;
    private final RmaCommentRepository rmaCommentRepository;
    private final ToolCommentRepository toolCommentRepository;
    private final TrackTrendRepository trackTrendRepository;
    private final TrackTrendCommentRepository trackTrendCommentRepository;

    @Autowired
    public DataInitializer(
            UserRepository userRepository,
            ToolRepository toolRepository,
            PassdownRepository passdownRepository,
            LocationRepository locationRepository,
            PasswordEncoder passwordEncoder,
            MapGridItemRepository mapGridItemRepository,
            RmaRepository rmaRepository,
            RmaCommentRepository rmaCommentRepository,
            ToolCommentRepository toolCommentRepository,
            TrackTrendRepository trackTrendRepository,
            TrackTrendCommentRepository trackTrendCommentRepository) {
        this.userRepository = userRepository;
        this.toolRepository = toolRepository;
        this.passdownRepository = passdownRepository;
        this.locationRepository = locationRepository;
        this.passwordEncoder = passwordEncoder;
        this.mapGridItemRepository = mapGridItemRepository;
        this.rmaRepository = rmaRepository;
        this.rmaCommentRepository = rmaCommentRepository;
        this.toolCommentRepository = toolCommentRepository;
        this.trackTrendRepository = trackTrendRepository;
        this.trackTrendCommentRepository = trackTrendCommentRepository;
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
            
            // Create historical RMAs
            createHistoricalRmas();
            
            // Create RMA comments
            createRmaComments();
            
            // Create tool comments
            createToolComments();
            
            // Create historical Passdowns
            createHistoricalPassdowns();
            
            // Create Track/Trends
            createTrackTrends();
            
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
        adminUser.setPassword(passwordEncoder.encode("admin123"));
        adminUser.setName("Admin User");
        adminUser.setRole("ADMIN");
        adminUser.setActive(true);
        
        // Set default location as active site if available
        Optional<Location> defaultLocation = locationRepository.findByDefaultLocationIsTrue();
        defaultLocation.ifPresent(adminUser::setActiveSite);
        
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
        
        // Set default location as active site if available
        Optional<Location> defaultLocation = locationRepository.findByDefaultLocationIsTrue();
        defaultLocation.ifPresent(techUser::setActiveSite);
        
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

    private void createHistoricalRmas() {
        logger.info("=== CHECKING HISTORICAL RMA CREATION ===");
        long rmaCount = rmaRepository.count();
        logger.info("Current RMA count: {}", rmaCount);
        
        if (rmaCount > 0) {
            logger.info("RMAs already exist, skipping historical RMA creation");
            return;
        }
        
        logger.info("Creating historical RMA data from CSV...");
        
        // Check if tools exist
        long toolCount = toolRepository.count();
        logger.info("Available tools count: {}", toolCount);
        if (toolCount == 0) {
            logger.warn("No tools found in database - RMA creation may fail");
        }
        
        // RMA Numbers from company records
        String[] rmaNumbers = {
            "300030625", "300030952", "300030947", "300030626", "300030633", "300030624", "300029336", "300031325",
            "300030951", "300031245", "300033169", "300031369", "300031311", "300031160", "300031359", "300031592",
            "300032324", "300031407", "300031498", "300031417", "300031923", "300031962", "300031409", "300031749",
            "300031763", "300031947", "300031926", "300032220", "300032229", "300032116", "300032144", "300032257",
            "300032380", "300032343", "300032115", "300032145", "300032224", "300032221", "300032223", "300032188"
        };
        
        // Random statuses - using only valid enum values
        RmaStatus[] statuses = {RmaStatus.RMA_WRITTEN_EMAILED, RmaStatus.NUMBER_PROVIDED, RmaStatus.MEMO_EMAILED, 
                               RmaStatus.RECEIVED_PARTS, RmaStatus.WAITING_CUSTOMER, RmaStatus.WAITING_FSE, RmaStatus.COMPLETED};
        
        // Generic comments from Travis and Duane
        String[] travisComments = {
            "Travis: Replaced faulty component, tested and verified working properly.",
            "Travis: Customer reported intermittent issues, running diagnostics.",
            "Travis: Completed repair and calibration, ready for pickup.",
            "Travis: Standard maintenance completed, all systems operational.",
            "Travis: Emergency repair completed ahead of schedule.",
            "Travis: Quality check passed, unit performing to specifications.",
            "Travis: Warranty work completed, no charge to customer.",
            "Travis: Software update applied, issue resolved.",
            "Travis: Preventive maintenance performed during repair.",
            "Travis: Rush job completed for critical production tool.",
            "Travis: Documented issue for engineering review.",
            "Travis: Completed troubleshooting, root cause identified.",
            "Travis: Unit tested extensively, all parameters within spec.",
            "Travis: Customer training provided on proper operation.",
            "Travis: Field repair completed at customer site."
        };
        
        String[] duaneComments = {
            "Duane: Initial inspection complete, ordering replacement parts.",
            "Duane: Part arrived damaged, requesting replacement from vendor.",
            "Duane: Troubleshooting electrical issue, may need circuit board replacement.",
            "Duane: Waiting for customer approval on additional repair costs.",
            "Duane: Part is backordered, customer notified of delay.",
            "Duane: Complex repair required, additional time needed.",
            "Duane: Investigating root cause of recurring failure.",
            "Duane: Mechanical adjustment made, retesting in progress.",
            "Duane: Collaborated with engineering on unusual failure mode.",
            "Duane: Detailed diagnostics completed, part replacement needed.",
            "Duane: Coordinating with vendor for expedited shipping.",
            "Duane: Customer consultation scheduled for repair options.",
            "Duane: Specialized tooling required for this repair.",
            "Duane: Multiple components need replacement, preparing quote.",
            "Duane: Environmental testing completed, unit ready for service."
        };
        
        // Historical RMA data from CSV - Fixed tool names to match actual tool names in database
        String[][] rmaData = {
            {"KOR151", "UPW Incident", "10/1/2024", "", "", "UPW Incident with contractor", "Track & Trend"},
            {"Atomizers", "Multiple Slurry Tools", "12/10/2024", "Yes", "Some", "Some has been cleaned and some were shipped out.", ""},
            {"KOR151", "Photohelic", "12/10/2024", "2/27/2025", "", "UPW Incident with contractor - Contractor quote for parts.", "Track & Trend"},
            {"BH151", "HV121", "11/14/2024", "4/24/2025", "4/24/2025", "Upw supply HV121 3/4\" Fluoroware leaking from weep hole - HV121 & HV221 were removed and placed in BH151D due to different model.", ""},
            {"BH151", "Commons Door", "10/31/2024", "4/18/2025", "", "Door was cracked from over torqued with electrical drill", ""},
            {"WL151D", "Inner connects", "11/15/2024", "", "", "1\" Prime Lock flare to 3/4\" male - Inner Connections for 5 and 7 Blend", "5/6/2025"},
            {"WK151D", "Feed Tank", "8/12/2024", "24-Sep", "", "While opening door. Door was heavy slipped and fell on grating. The door cracked on bottom.", "4/9/2025"},
            {"TM151D", "IY replacements", "12/11/2024", "1/28/2025", "", "2 IY for the scale needed for install", "Yes"},
            {"JS151D", "CF01B Grey Peg", "12/10/2024", "12/18/2025", "", "Grey Standoff and plastic screw for CF01B - Received parts from Mike Southwell he had extra", "Yes"},
            {"AH151", "HG01 hook & PS003", "12/16/2024", "Yes", "Yes", "PS003 not functioning correctly and Broken HG01 Hook", ""},
            {"EF151D", "HG02 hook & Tank 1 lid handle", "12/19/2024", "Yes", "", "HG02 Hook Broken and Tank 1 Lid handle Broken", "5/23/2025"},
            {"SSC151", "PV005", "12/30/2024", "4/18/2025", "4/22/2025", "PV005 Leaking from weep hole. RMA for SSC151D PV005 will go to KF152", ""},
            {"KG151", "PV005", "12/30/2024", "4/18/2025", "4/22/2025", "PV005 Leaking from weep hole", ""},
            {"VC151D", "PV009/PC009", "12/30/2024", "1/28/2025", "Yes", "Feed PV009/PC009 Failed", ""},
            {"VC151D", "Wiring Track and Trend", "12/23/2024", "", "", "AT24 was not registering a reading. It was found that the green wire 467 was landed in the com location just above V/I 3- instead of V/I 3- itself.", "Track & Trend"},
            {"FU151D", "AT023/AT025 wiring", "1/6/2025", "1/13/2025", "", "AT023/AT025 Wiring - Finishing kit", "AT025 - 1/14/2025"},
            {"VC151D", "fast n tight reducer", "12/23/2024", "2/26/2025", "", "Leaking OFA from Fast n tight fitting in feed station after HV031 feeds OFA to PV009/PC009 DP01", "Installed"},
            {"JP151D", "Tank 1", "1/14/2025", "4/10/2025", "", "JP151 Tank 1 cracked", ""},
            {"JT151", "AE24", "1/14/2025", "Yes", "Yes", "Damaged while installing in tool", "In JP151 parts kit"},
            {"KOR151", "McMillan", "1/16/2025", "2/26/2025", "", "McMillan failed not holding set point as desired", "3/20/2025"},
            {"FU151D", "Stylus", "1/15/2025", "2/26/2025", "", "Stylus missing tip", ""},
            {"HG151D", "HV035", "1/30/2025", "4/3/2025", "Yes", "Leaking UPW from weephole", ""},
            {"HG151D", "PV015", "2/3/2025", "", "Yes", "Water pressure hammering due to regulator. Also broke the needle on the gauge", ""},
            {"TM151D", "Dist Pump 1 IY3", "1/8/2025", "1/28/2025", "", "IY3 and PLC Interface not working well. Adjusted and could not calibrate", ""},
            {"WC151D", "PV004", "2/7/2025", "2/20/2025", "Yes", "Leaking UPW from weephole", ""},
            {"WC151D", "3-way valve base", "2/7/2025", "", "Yes", "Broken tabs on valve base", ""},
            {"HG151D", "1/4\" elbow", "2/20/2025", "", "", "Leaking 1/4\" elbow at FT030", ""},
            {"EK151D", "Feed Tank", "2/18/2025", "", "", "EK151D - Found small dots on the bottom of tank thank looked like rust. Unsure", ""},
            {"EK151D", "LPC Cal. Loop & Return Supply", "2/18/2025", "3/20/2025", "", "EK151D - LPC Cal. Loop & Return Supply both fittings were broken and leaking", ""},
            {"EK151D", "Blue Tags HV064 & HV065", "2/18/2025", "3/20/2025", "", "EK151D - Blue Tags HV064 & HV065 were found as CV064 &CV065", ""},
            {"EK151D", "Feed Non-Commpression fitting", "2/18/2025", "3/25/2025", "", "EK151D - Feed Non-Commpression fitting that is off of the main OFA manifold feeding to PV010", ""},
            {"BT151", "DT1 and DT2 Filters leaking", "2/18/2025", "4/2/2025", "", "BT151D - DT1 and DT2 Filters leaking", ""},
            {"WR151D", "Tank 2 Crack and indent", "2/18/2025", "4/15/2025", "", "WR151D - Tank 2 Crack and indent", ""},
            {"WR151D", "Tank 1 Track and trend", "2/26/2025", "", "", "WR151D - Tank 1 found a piece of broken plastic screw at the bottom of tank drain manifold.", "Track & Trend"},
            {"BT152", "PV005, PV202, HV121", "2/26/2025", "4/18/2025", "", "PV005 not holding pressure, HV121 leaking at weep hole, PV202 not depressureizing correctly.", "5/12/2025"},
            {"BT152", "Flow Controller Cable", "2/26/2025", "4/10/2025", "", "FC connector for UPW FC 121, was found damaged when removing heat shrink from connector.", "2/17/2025"},
            {"HD152", "PV005", "2/27/2025", "4/18/2025", "4/22/2025", "Leaking from weephole. This part was removed and installed into BT152D NEEDED TO START TOOL UP ASAP", ""},
            {"AH151", "Leaking Filters", "3/5/2025", "4/2/2025", "", "AH151D - Leaking filters on FL102 & FL201.", "Track & Trend"},
            {"JP151D", "PV009/PC009", "3/6/2025", "3/14/2025", "", "JP151F - PV009/PC009 failed after", "Installed"},
            {"JP151D", "FT002", "3/6/2025", "", "", "JP151F FT002 Wiring landed wrong", "Track & Trend"},
            {"RE151D", "Tank 2 Crack", "3/11/2025", "4/15/2025", "", "RE151D - Crack Tank 2", ""},
            {"GR151", "CV203", "3/12/2025", "5/16/2025", "", "GR151D - CV203", "5/20/2025"},
            {"WK151D", "CP02", "3/13/2025", "4/2/2025", "", "CP02 leaking from side wall one of the plastic screw is not tightening", "Track & Trend"},
            {"WK151D", "PV015, PI012, FT030", "3/21/2025", "", "", "Water pressure hammering due to regulator. Also broke the needle on the gauge. FT030 not giving feedback to FC030", "5/19/2025"},
            {"WK151D", "PI012", "4/14/2025", "5/7/2025", "", "This part was replaced before and was damaged during start up.", ""},
            {"JK151D", "Feed tank leak", "4/15/2025", "", "", "Cracked weld on Feed tank bottom fitting", ""},
            {"JK151D", "Leak sensor retaining clips", "4/15/2025", "5/16/2025", "", "Missing leak sensor retaining clips", "5/21/2025"},
            {"KF151", "PV005", "3/19/2025", "4/3/2025", "", "PV005 is Leaking from weep hole", ""},
            {"RE151D", "FT002", "3/19/2025", "5/27/2025", "", "Intel was pulling chemical from the drum and noticed the FT002 on flow was constantly reading 0.", ""},
            {"AR151", "PV005", "4/8/2025", "", "", "PV005 is Leaking from weep hole", ""},
            {"KOR152", "Power Supply", "4/14/2025", "", "4/14/2025", "Power Supply 4PWS Sparked and smoked when power was turned on by Intel", "4/11/2025"},
            {"XJ151D", "NV01 & NV02", "4/17/2025", "4/23/2025", "", "Valves are stripped on thread can't close or open. The valves keep turning.", ""},
            {"JK151D", "Distribution Tank 2", "4/17/2025", "", "", "Distribution Tank 2 had gouge on the left side of the tank interior and scratch on the outerside of tank near gouge.", "Track & Trend"},
            {"JK151D", "Distribution Tank 1", "4/17/2025", "", "", "Tank 1 has gouge on righ side of tank", "Track & Trend"},
            {"JK151D", "Feed Tank", "4/15/2025", "", "", "Tank was leaking from weld on bottom of tank", ""},
            {"JK151D", "Level sensor Clips", "4/15/2025", "", "", "Level sensors were missing retaining clips", ""},
            {"JK151D", "Pneumatic lines swapped", "4/21/2025", "", "", "Feed and Blend solenoid lines were swapped front to back", "Track & Trend"},
            {"RAK151D", "SOL #2", "4/10/2025", "", "", "Pin # 3 bent on SOL #2 wulit-wire connector, could not make connection", ""},
            {"JP151D", "Flow line ultrasonic sensor", "4/28/2025", "", "", "Sensor was stuck on 100% ful when tank was empty and filled at 50%", ""},
            {"JK151D", "HV013 Leak", "4/29/2025", "Yes", "Yes", "Intel was flushing system with UPW and noticed HV013 was leaking from side wall.", ""},
            {"KT151D", "Tank 2 gouge", "5/1/2025", "", "", "Tank 2 has a gouge in the back of the tank.", ""},
            {"RAK151D", "PI012", "4/8/2025", "5/5/2025", "Yes", "Pressure gauge needle broke and was not functioning correctly. The needle was slamming hard.", "5/19/2025"},
            {"KT151D", "Feed tank scratches", "5/7/2025", "", "", "Scratches and small gouges in Feed Tank", "Track and Trend"},
            {"KT151D", "PV004", "5/12/2025", "5/27/2025", "", "Leaking from weephole", ""},
            {"JP151D", "non compression fitting", "5/16/2025", "", "", "Non compression fitting blew off", ""},
            {"KT151D", "Oversized distribution tank", "5/14/2025", "", "", "Replacement tank was oversized and the standoff pegs had to be trimmed to make the tank fit", "Track and Trend"},
            {"KT151D", "NV003 leaking", "5/20/2025", "", "", "NV003 leaking even after tightening", ""},
            {"RAK152D", "HV038 leaking by", "5/22/2025", "", "", "HV038 leaking by", ""}
        };
        
        int successCount = 0;
        int failureCount = 0;
        Random random = new Random();
        
        for (int i = 0; i < rmaData.length; i++) {
            try {
                String[] rmaRow = rmaData[i];
                
                // Get RMA number (cycle through if we have more RMAs than numbers)
                String rmaNumber = rmaNumbers[i % rmaNumbers.length];
                
                // Get random status
                RmaStatus randomStatus = statuses[random.nextInt(statuses.length)];
                
                // Get random comment (50/50 chance Travis or Duane)
                String randomComment;
                if (random.nextBoolean()) {
                    randomComment = travisComments[random.nextInt(travisComments.length)];
                } else {
                    randomComment = duaneComments[random.nextInt(duaneComments.length)];
                }
                
                createHistoricalRma(rmaRow, rmaNumber, randomStatus, randomComment);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                logger.warn("Failed to create historical RMA for tool {}: {}", rmaData[i][0], e.getMessage());
            }
        }
        
        logger.info("Historical RMA data creation complete: {} successful, {} failed", successCount, failureCount);
        
        // Final count
        long finalRmaCount = rmaRepository.count();
        logger.info("Final RMA count in database: {}", finalRmaCount);
    }
    
    private void createHistoricalRma(String[] rmaRow, String rmaNumber, RmaStatus randomStatus, String randomComment) {
        String toolName = rmaRow[0];
        String partName = rmaRow[1];
        String writtenDate = rmaRow[2];
        String partsReceivedDate = rmaRow[3];
        String partsShippedDate = rmaRow[4];
        String comments = rmaRow[5];
        String closedIndicator = rmaRow[6];
        
        // Skip if essential info is missing
        if (toolName == null || toolName.trim().isEmpty() || partName == null || partName.trim().isEmpty()) {
            return;
        }
        
        // Find the tool by name
        Optional<Tool> toolOpt = toolRepository.findByName(toolName.trim());
        if (!toolOpt.isPresent()) {
            logger.warn("Tool not found: '{}'. Available tools: {}", toolName, 
                      toolRepository.findAll().stream()
                          .map(Tool::getName)
                          .limit(10)
                          .toArray());
            return;
        }
        
        logger.debug("Found tool: {} for RMA part: {}", toolName, partName);
        
        Tool tool = toolOpt.get();
        Optional<Location> defaultLocation = locationRepository.findByDefaultLocationIsTrue();
        
        // Create the RMA
        Rma rma = new Rma();
        rma.setTool(tool);
        rma.setRmaNumber(rmaNumber);  // Use the provided RMA number
        
        // Set location to default if available
        defaultLocation.ifPresent(rma::setLocation);
        
        // Parse and set written date
        if (writtenDate != null && !writtenDate.trim().isEmpty()) {
            try {
                LocalDate date = parseFlexibleDate(writtenDate.trim());
                if (date != null) {
                    rma.setWrittenDate(date);
                }
            } catch (Exception e) {
                logger.debug("Could not parse written date: {}", writtenDate);
            }
        }
        
        // Parse and set parts received date
        if (partsReceivedDate != null && !partsReceivedDate.trim().isEmpty() && 
            !partsReceivedDate.equalsIgnoreCase("Yes") && !partsReceivedDate.equalsIgnoreCase("N/A")) {
            try {
                LocalDate date = parseFlexibleDate(partsReceivedDate.trim());
                if (date != null) {
                    rma.setPartsReceivedDate(date);
                }
            } catch (Exception e) {
                logger.debug("Could not parse parts received date: {}", partsReceivedDate);
            }
        }
        
        // Parse and set shipped date
        if (partsShippedDate != null && !partsShippedDate.trim().isEmpty() && 
            !partsShippedDate.equalsIgnoreCase("Yes") && !partsShippedDate.equalsIgnoreCase("N/A")) {
            try {
                LocalDate date = parseFlexibleDate(partsShippedDate.trim());
                if (date != null) {
                    rma.setFailedPartsShippedDate(date);
                }
            } catch (Exception e) {
                logger.debug("Could not parse shipped date: {}", partsShippedDate);
            }
        }
        
        // Use the random status instead of original logic
        rma.setStatus(randomStatus);
        
        // Set priority and reason
        rma.setPriority(RmaPriority.MEDIUM);
        rma.setReasonForRequest(RmaReasonForRequest.WARRANTY_REPLACEMENT);
        
        // Set problem information with original comments and random technician comments
        String combinedComments = "";
        if (comments != null && !comments.trim().isEmpty()) {
            combinedComments = comments.trim() + "\n\n" + randomComment;
        } else {
            combinedComments = randomComment;
        }
        rma.setWhatHappened(combinedComments);
        
        // Create part line item
        PartLineItem partLineItem = new PartLineItem();
        partLineItem.setPartName(partName.trim());
        partLineItem.setQuantity(1);
        partLineItem.setReplacementRequired(true);
        
        List<PartLineItem> partLineItems = new ArrayList<>();
        partLineItems.add(partLineItem);
        rma.setPartLineItems(partLineItems);
        
        // Save the RMA
        rmaRepository.save(rma);
        
        logger.debug("Created historical RMA for tool {} with part {}", toolName, partName);
    }
    
    private void createRmaComments() {
        logger.info("Creating RMA comments...");
        
        // Check if comments already exist
        if (rmaCommentRepository.count() > 0) {
            logger.info("RMA comments already exist, skipping comment creation");
            return;
        }
        
        List<Rma> allRmas = rmaRepository.findAll();
        if (allRmas.isEmpty()) {
            logger.warn("No RMAs found, cannot create comments");
            return;
        }
        
        // Get users for comments
        List<User> users = userRepository.findAll();
        if (users.isEmpty()) {
            logger.warn("No users found, cannot create comments");
            return;
        }
        
        Random random = new Random();
        
        // Comment content arrays
        String[] techComments = {
            "Initial assessment complete. Ordering replacement parts from vendor.",
            "Part received and inspected. Beginning repair work.",
            "Repair completed successfully. Testing all functions.",
            "Quality control check passed. Ready for customer pickup.",
            "Customer notified of completion. Scheduling delivery.",
            "Issue resolved. Root cause identified as normal wear.",
            "Warranty claim approved. No charge to customer.",
            "Additional testing required due to complexity of failure.",
            "Collaborating with engineering team on unusual failure mode.",
            "Expedited repair due to customer production requirements.",
            "Preventive maintenance recommendations provided to customer.",
            "Software update applied along with hardware repair.",
            "Field service support requested for on-site installation.",
            "Training provided to customer technician during delivery.",
            "Documentation updated with repair details and recommendations."
        };
        
        String[] statusComments = {
            "Status updated - waiting for customer response on quote.",
            "Parts shipped from supplier. Expected delivery tomorrow.",
            "Customer approved additional repair work. Proceeding.",
            "Temporary hold pending availability of specialized technician.",
            "Priority upgraded due to customer production impact.",
            "Coordinating with customer for convenient pickup time.",
            "Final inspection completed. All specifications met.",
            "Rush delivery requested by customer. Coordinating logistics.",
            "Additional documentation required for warranty claim.",
            "Customer satisfied with repair quality and turnaround time."
        };
        
        String[] followUpComments = {
            "Follow-up call scheduled for next week to check performance.",
            "Recommended maintenance schedule provided to customer.",
            "Similar issue patterns noted. Engineering review initiated.",
            "Customer feedback positive. No further issues reported.",
            "Monitoring for any recurring issues with this part type.",
            "Vendor notified of part quality concerns for improvement.",
            "Standard repair completed within expected timeframe.",
            "Customer training completed on proper operation procedures.",
            "Backup parts inventory updated based on failure analysis.",
            "Process improvement suggestions documented for future reference."
        };
        
        int commentsCreated = 0;
        
        // Create 0-3 comments per RMA, with some having 5-6
        for (Rma rma : allRmas) {
            int numComments;
            // 85% chance of 0-3 comments, 15% chance of 5-6 comments
            if (random.nextDouble() < 0.85) {
                numComments = random.nextInt(4); // 0 to 3 comments
            } else {
                numComments = 5 + random.nextInt(2); // 5 or 6 comments
            }
            
            for (int i = 0; i < numComments; i++) {
                RmaComment comment = new RmaComment();
                comment.setRma(rma);
                comment.setUser(users.get(random.nextInt(users.size())));
                
                // Select comment type based on order
                String content;
                if (i == 0) {
                    content = techComments[random.nextInt(techComments.length)];
                } else if (i == 1) {
                    content = statusComments[random.nextInt(statusComments.length)];
                } else {
                    content = followUpComments[random.nextInt(followUpComments.length)];
                }
                
                comment.setContent(content);
                
                // Set creation date randomly within the past 3 weeks
                LocalDateTime baseDate = LocalDateTime.now().minusWeeks(3);
                long randomDays = random.nextInt(21); // 0-20 days
                long randomHours = random.nextInt(24);
                comment.setCreatedDate(baseDate.plusDays(randomDays).plusHours(randomHours));
                
                rmaCommentRepository.save(comment);
                commentsCreated++;
            }
        }
        
        logger.info("Created {} RMA comments across {} RMAs", commentsCreated, allRmas.size());
    }
    
    private void createToolComments() {
        logger.info("Creating tool comments...");
        
        // Check if comments already exist
        long existingCommentCount = toolCommentRepository.count();
        logger.info("Found {} existing tool comments in database", existingCommentCount);
        
        // TEMPORARY: Force recreation of tool comments for debugging
        if (existingCommentCount > 0) {
            logger.info("Deleting existing {} tool comments to recreate them", existingCommentCount);
            toolCommentRepository.deleteAll();
            logger.info("Deleted all existing tool comments, proceeding with creation");
        }
        
        List<Tool> allTools = toolRepository.findAll();
        logger.info("Found {} tools for comment creation", allTools.size());
        if (allTools.isEmpty()) {
            logger.warn("No tools found, cannot create comments");
            return;
        }
        
        // Get users for comments
        List<User> users = userRepository.findAll();
        logger.info("Found {} users for comment creation", users.size());
        if (users.isEmpty()) {
            logger.warn("No users found, cannot create comments");
            return;
        }
        
        Random random = new Random();
        
        // Tool comment content arrays
        String[] maintenanceComments = {
            "Completed routine calibration check. All parameters within specification. Tool ready for production.",
            "Performed quarterly preventive maintenance. Replaced filters and lubricated moving parts.",
            "Updated tool software to latest version. Performance improvements noted in testing.",
            "Cleaned process chambers and replaced worn seals. System performance back to baseline.",
            "Conducted annual certification inspection. All safety systems functioning properly.",
            "Replaced aging temperature sensor that was showing drift. Calibration verified.",
            "Performed deep cleaning of slurry delivery system. Flow rates now consistent.",
            "Updated PLC program with latest process improvements from engineering.",
            "Replaced worn pump components during scheduled downtime. Testing shows improved reliability.",
            "Completed tool qualification after major maintenance. All specs met or exceeded."
        };
        
        String[] operationalComments = {
            "Tool running smoothly after recent process optimization. Customer very satisfied with results.",
            "Minor alarm occurred during shift - traced to environmental condition. No action required.",
            "Process recipe updated per customer request. Initial results look promising.",
            "Tool utilization increased to 95% this month. Excellent performance metrics.",
            "Customer technician training completed successfully. They are now fully certified.",
            "New process window established for customer's latest product. Validation in progress.",
            "Tool performed flawlessly during 72-hour stress test. Reliability confirmed.",
            "Productivity targets exceeded for third consecutive month. Outstanding performance.",
            "Customer audit completed with zero findings. Tool documentation up to date.",
            "Process yield improved 3% after implementing engineering recommendations."
        };
        
        String[] troubleshootingComments = {
            "Investigated intermittent pressure alarm. Root cause identified and corrected.",
            "Responded to customer concern about process variation. Adjusted control parameters.",
            "Electrical noise issue resolved by improving cabinet grounding. System stable.",
            "Flow controller replaced after showing erratic behavior. Backup unit installed.",
            "Temperature control instability traced to faulty RTD. Replacement ordered.",
            "Pneumatic actuator response slow. Cleaned and adjusted valve trim successfully.",
            "Process timing optimized to reduce cycle time while maintaining quality.",
            "HMI touchscreen replaced due to responsiveness issues. System restored.",
            "Chemical delivery pump rebuilt after 18 months of service. Performance excellent.",
            "Vacuum system performance degraded. Scheduled thorough inspection and cleaning."
        };
        
        String[] qualityComments = {
            "Quality control inspection passed with flying colors. All measurements within tolerance.",
            "Process capability study completed. Cpk values exceed customer requirements.",
            "Statistical process control charts show excellent stability over past month.",
            "Customer quality audit successful. Commended for documentation and procedures.",
            "Metrology verification completed. All measurement systems certified accurate.",
            "Process validation package approved by customer quality team. Production authorized.",
            "Continuous improvement project implemented. Defect rate reduced by 40%.",
            "Six Sigma analysis identified key process parameters for optimization.",
            "Quality metrics dashboard updated with real-time performance indicators.",
            "Best practices documented and shared with other similar tools in facility."
        };
        
        String[] teamComments = {
            "Great teamwork during emergency repair. Tool back online ahead of schedule.",
            "Excellent collaboration between engineering and operations teams on this improvement.",
            "Training session well received by technicians. Knowledge retention test scores high.",
            "Cross-functional team meeting scheduled to review lessons learned.",
            "Mentoring new technician going well. They show strong aptitude for troubleshooting.",
            "Process knowledge transfer completed from day shift to night shift team.",
            "Safety meeting highlighted importance of proper PPE during maintenance.",
            "Team celebration for achieving 6 months without lost time incident.",
            "Suggestion box idea implemented successfully. Team member recognized.",
            "Communication improvement noted between shifts. Handoff quality much better."
        };
        
        int commentsCreated = 0;
        
        // Create 1-3 comments per tool, with some having 5-6 (every tool gets at least 1 comment)
        for (Tool tool : allTools) {
            int numComments;
            // 85% chance of 1-3 comments, 15% chance of 5-6 comments
            if (random.nextDouble() < 0.85) {
                numComments = 1 + random.nextInt(3); // 1 to 3 comments
            } else {
                numComments = 5 + random.nextInt(2); // 5 or 6 comments
            }
            
            for (int i = 0; i < numComments; i++) {
                ToolComment comment = new ToolComment();
                comment.setTool(tool);
                comment.setUser(users.get(random.nextInt(users.size())));
                
                // Select comment type based on random distribution
                String content;
                double commentType = random.nextDouble();
                if (commentType < 0.3) {
                    content = maintenanceComments[random.nextInt(maintenanceComments.length)];
                } else if (commentType < 0.5) {
                    content = operationalComments[random.nextInt(operationalComments.length)];
                } else if (commentType < 0.7) {
                    content = troubleshootingComments[random.nextInt(troubleshootingComments.length)];
                } else if (commentType < 0.9) {
                    content = qualityComments[random.nextInt(qualityComments.length)];
                } else {
                    content = teamComments[random.nextInt(teamComments.length)];
                }
                
                comment.setContent(content);
                
                // Set creation date randomly within the past 4 weeks
                LocalDateTime baseDate = LocalDateTime.now().minusWeeks(4);
                long randomDays = random.nextInt(28); // 0-27 days
                long randomHours = random.nextInt(24);
                long randomMinutes = random.nextInt(60);
                comment.setCreatedDate(baseDate.plusDays(randomDays).plusHours(randomHours).plusMinutes(randomMinutes));
                
                toolCommentRepository.save(comment);
                commentsCreated++;
            }
        }
        
        logger.info("Created {} tool comments across {} tools", commentsCreated, allTools.size());
        
        // Final verification - check actual count in database
        long finalCount = toolCommentRepository.count();
        logger.info("Final tool comment count in database: {}", finalCount);
    }
    
    private void createHistoricalPassdowns() {
        logger.info("Creating historical passdowns...");
        
        // Check if passdowns already exist
        if (passdownRepository.count() > 0) {
            logger.info("Passdowns already exist, skipping passdown creation");
            return;
        }
        
        List<Tool> tools = toolRepository.findAll();
        List<User> users = userRepository.findAll();
        
        if (tools.isEmpty() || users.isEmpty()) {
            logger.warn("No tools or users found, cannot create passdowns");
            return;
        }
        
        Random random = new Random();
        
        // Passdown content arrays
        String[] maintenanceComments = {
            "Completed routine maintenance check. All systems operating normally. Lubricated moving parts and checked all connections.",
            "Performed daily calibration verification. Minor adjustment made to pressure sensor. Tool running within specifications.",
            "Cleaned feed lines and replaced inline filters. System flow rates improved significantly after maintenance.",
            "Investigated customer report of intermittent alarm. Found loose electrical connection in panel. Repaired and tested.",
            "Replaced worn valve actuator that was causing slow response times. Customer satisfied with improved performance.",
            "Updated tool software to latest version. All previous bugs resolved, system stability improved.",
            "Completed preventive maintenance per schedule. No issues found. Next service due in 6 months.",
            "Responded to emergency call for system shutdown. Faulty sensor replaced, tool back in production.",
            "Installed customer-requested modifications to improve throughput. Testing shows 15% improvement.",
            "Performed annual calibration and certification. All measurements within tolerance. Certificate updated."
        };
        
        String[] troubleshootingComments = {
            "Tool throwing intermittent flow alarms. Checked all connections and cleaned sensors. Monitoring for stability.",
            "Customer reported erratic readings on Tank 2 level sensor. Sensor appears to be failing. Ordering replacement.",
            "Pressure control system not maintaining setpoint. Suspect regulator issue. Scheduled detailed inspection.",
            "Feed pump running rough. Checked alignment and coupling. May need bearing replacement soon.",
            "HMI screen showing occasional communication errors. Updated drivers and reset network settings.",
            "Chemical delivery timing seems inconsistent. Investigating valve response times and control logic.",
            "Temperature control hunting around setpoint. Tuned PID parameters for better stability.",
            "Flow controller giving erroneous readings. Suspect electronic module failure. Testing continues.",
            "Multiple nuisance alarms on startup sequence. Reviewing logic and adjusting delay timers.",
            "Customer requests training on new operating procedures. Session scheduled for next week."
        };
        
        String[] completionComments = {
            "Successfully completed all scheduled maintenance tasks. Tool performance excellent. Customer very satisfied.",
            "Issue resolved after replacing faulty component. Tool tested thoroughly and returned to service.",
            "Modification work completed ahead of schedule. Customer production resumed with improved efficiency.",
            "Training session completed with customer technicians. They are now fully qualified on new procedures.",
            "Emergency repair completed in under 2 hours. Customer production downtime minimized.",
            "Quality inspection passed with flying colors. Tool meets all specifications and requirements.",
            "Software upgrade successful. All new features operational and customer trained on usage.",
            "Parts replacement completed using warranty coverage. No charge to customer for repair.",
            "Performance optimization complete. Tool now exceeds original specification by 10%.",
            "Annual certification completed successfully. Tool approved for another year of operation."
        };
        
        int passdownsCreated = 0;
        
        // Create passdowns for the past 3 weeks
        LocalDate startDate = LocalDate.now().minusWeeks(3);
        LocalDate endDate = LocalDate.now();
        
        // Create 2-8 passdowns per day
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            int numPassdowns = 2 + random.nextInt(7); // 2 to 8 passdowns per day
            
            for (int i = 0; i < numPassdowns; i++) {
                Passdown passdown = new Passdown();
                passdown.setDate(date);
                passdown.setUser(users.get(random.nextInt(users.size())));
                
                // 80% chance to assign to a tool, 20% chance for general passdown
                if (random.nextDouble() < 0.8) {
                    passdown.setTool(tools.get(random.nextInt(tools.size())));
                }
                
                // Select comment type based on random distribution
                String comment;
                double commentType = random.nextDouble();
                if (commentType < 0.4) {
                    comment = maintenanceComments[random.nextInt(maintenanceComments.length)];
                } else if (commentType < 0.8) {
                    comment = troubleshootingComments[random.nextInt(troubleshootingComments.length)];
                } else {
                    comment = completionComments[random.nextInt(completionComments.length)];
                }
                
                passdown.setComment(comment);
                
                // Set creation time randomly during the day
                LocalDateTime createdDateTime = date.atTime(8 + random.nextInt(10), random.nextInt(60)); // 8 AM to 6 PM
                passdown.setCreatedDate(createdDateTime);
                
                passdownRepository.save(passdown);
                passdownsCreated++;
            }
        }
        
        logger.info("Created {} historical passdowns over {} days", passdownsCreated, 
                   startDate.until(endDate).getDays() + 1);
    }
    
    private void createTrackTrends() {
        logger.info("Creating Track/Trends...");
        
        // Check if Track/Trends already exist
        if (trackTrendRepository.count() > 0) {
            logger.info("Track/Trends already exist, skipping Track/Trend creation");
            return;
        }
        
        List<Tool> tools = toolRepository.findAll();
        List<User> users = userRepository.findAll();
        
        if (tools.isEmpty() || users.isEmpty()) {
            logger.warn("No tools or users found, cannot create Track/Trends");
            return;
        }
        
        Random random = new Random();
        
        // Track/Trend data with realistic titles and descriptions
        String[][] trackTrendData = {
            {"Valve Weep Hole Leaking Pattern", "Multiple tools experiencing weep hole leaks in pressure valves. Investigating if this is a batch issue or wear pattern."},
            {"Feed Tank Structural Issues", "Several feed tanks showing cracks and gouges. Investigating manufacturing defects or installation issues."},
            {"Pressure Control Inconsistency", "Pressure regulators not maintaining setpoints consistently across multiple tools. May be supplier quality issue."},
            {"HMI Communication Errors", "Intermittent communication failures between HMI screens and PLCs. Software or network infrastructure related."},
            {"Flow Controller Calibration Drift", "Flow controllers requiring frequent recalibration. Investigating sensor degradation or electronic module issues."},
            {"UPW Supply Contamination Events", "Multiple contamination incidents in UPW supply lines. Investigating source and mitigation strategies."},
            {"Chemical Delivery Timing Issues", "Inconsistent chemical delivery timing affecting process repeatability. Control logic or valve response investigation."},
            {"Temperature Control Hunting", "Temperature controllers oscillating around setpoints rather than maintaining steady state."},
            {"Level Sensor False Readings", "Tank level sensors providing erroneous readings. Investigating sensor reliability and calibration procedures."},
            {"Pneumatic System Pressure Loss", "Gradual pressure loss in pneumatic systems requiring investigation of fittings and actuators."},
            {"Filter Premature Clogging", "Inline filters clogging faster than expected. Investigating upstream contamination sources."},
            {"Pump Bearing Wear Pattern", "Chemical pumps showing accelerated bearing wear. Investigating chemical compatibility and maintenance schedules."},
            {"Electrical Connector Corrosion", "Corrosion found in electrical connectors in chemical areas. Environmental protection assessment needed."},
            {"Software Update Compatibility", "Issues arising after software updates. Compatibility testing and rollback procedures review."},
            {"Calibration Equipment Drift", "Test equipment used for calibration showing drift. Metrology standards review required."},
            {"Emergency Shutdown Delays", "Emergency shutdown sequences taking longer than specified. Safety system optimization needed."},
            {"Chemical Cross-Contamination", "Trace contamination between different chemical lines. Purge procedures and isolation review."},
            {"Vibration Analysis Abnormalities", "Unusual vibration patterns detected during routine monitoring. Mechanical integrity assessment."},
            {"Power Supply Fluctuations", "Electrical power quality issues affecting sensitive equipment. Infrastructure stability investigation."},
            {"Documentation Discrepancies", "Procedures not matching actual field configurations. Documentation update and training needed."}
        };
        
        // Comments for Track/Trends
        String[] investigationComments = {
            "Initial data collection complete. Pattern observed across 5 tools in the last month.",
            "Engineering review scheduled for next week. Preliminary findings suggest supplier issue.",
            "Correlation found with environmental conditions. Temperature and humidity factors being investigated.",
            "Maintenance procedures updated based on initial findings. Monitoring effectiveness.",
            "Customer notification sent regarding potential impact. No production delays expected.",
            "Vendor contacted for technical support. Expecting response within 48 hours.",
            "Temporary workaround implemented while investigating root cause. Monitoring stability.",
            "Historical data analysis reveals this is a recurring seasonal issue. Preventive measures needed.",
            "Similar issue reported at other sites. Industry-wide investigation may be warranted.",
            "Cost-benefit analysis complete. Replacement parts ordered for affected tools."
        };
        
        String[] progressComments = {
            "Root cause identified as supplier manufacturing defect. Batch recall initiated.",
            "Software patch developed and tested. Rollout scheduled for next maintenance window.",
            "Training session completed with all technicians. Updated procedures in effect.",
            "Monitoring data shows improvement after implementing corrective actions.",
            "Quality control measures enhanced to prevent recurrence. New inspection protocols active.",
            "Engineering change request submitted for permanent fix. Awaiting approval.",
            "Field modification kits received from supplier. Installation starting this week.",
            "Process parameters adjusted based on investigation findings. Performance improving.",
            "Additional sensors installed for better monitoring. Data collection ongoing.",
            "Preventive maintenance schedule updated to address identified failure modes."
        };
        
        String[] resolutionComments = {
            "Issue resolved through component replacement. No further incidents in 2 weeks.",
            "Process optimization complete. Performance now exceeds original specifications.",
            "Long-term solution implemented. Monitoring will continue for 30 days before closure.",
            "All affected tools updated. Customer satisfaction ratings improved significantly.",
            "Knowledge base updated with lessons learned. Training materials revised.",
            "Supplier corrective action verified effective. Quality agreement updated.",
            "Emergency response procedures updated based on incident learnings.",
            "Cost savings achieved through process improvement. ROI exceeded expectations.",
            "Industry best practices adopted. Sharing findings with professional networks.",
            "Continuous improvement process established to prevent similar issues."
        };
        
        int trackTrendsCreated = 0;
        
        // Create Track/Trends
        for (int i = 0; i < trackTrendData.length; i++) {
            String[] ttData = trackTrendData[i];
            
            TrackTrend trackTrend = new TrackTrend();
            trackTrend.setName(ttData[0]);
            trackTrend.setDescription(ttData[1]);
            
            // Assign 1-5 random tools to each Track/Trend
            Set<Tool> affectedTools = new HashSet<>();
            int numTools = 1 + random.nextInt(5); // 1 to 5 tools
            Set<Integer> usedToolIndices = new HashSet<>();
            
            for (int j = 0; j < numTools && usedToolIndices.size() < tools.size(); j++) {
                int toolIndex;
                do {
                    toolIndex = random.nextInt(tools.size());
                } while (usedToolIndices.contains(toolIndex));
                
                usedToolIndices.add(toolIndex);
                affectedTools.add(tools.get(toolIndex));
            }
            
            trackTrend.setAffectedTools(affectedTools);
            
            // Save the Track/Trend first
            TrackTrend savedTrackTrend = trackTrendRepository.save(trackTrend);
            
            // Add 2-6 comments per Track/Trend
            int numComments = 2 + random.nextInt(5); // 2 to 6 comments
            for (int k = 0; k < numComments; k++) {
                TrackTrendComment comment = new TrackTrendComment();
                comment.setTrackTrend(savedTrackTrend);
                comment.setUser(users.get(random.nextInt(users.size())));
                
                // Select comment type based on order
                String content;
                if (k < 2) {
                    content = investigationComments[random.nextInt(investigationComments.length)];
                } else if (k < 4) {
                    content = progressComments[random.nextInt(progressComments.length)];
                } else {
                    content = resolutionComments[random.nextInt(resolutionComments.length)];
                }
                
                comment.setContent(content);
                
                // Set creation date randomly within the past 4 weeks
                LocalDateTime baseDate = LocalDateTime.now().minusWeeks(4);
                long randomDays = random.nextInt(28); // 0-27 days
                long randomHours = random.nextInt(24);
                comment.setCreatedDate(baseDate.plusDays(randomDays).plusHours(randomHours));
                
                trackTrendCommentRepository.save(comment);
            }
            
            trackTrendsCreated++;
        }
        
        logger.info("Created {} Track/Trends with associated comments", trackTrendsCreated);
    }
    
    private LocalDate parseFlexibleDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        dateStr = dateStr.trim();
        
        // Try different date formats
        String[] patterns = {
            "M/d/yyyy",    // 10/1/2024
            "MM/dd/yyyy",  // 12/10/2024  
            "d-MMM",       // 24-Sep (assume current year)
            "M/d/yy",      // 1/14/25
            "MM/dd/yy",    // 12/18/25
            "yyyy-MM-dd"   // 2025-01-14
        };
        
        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                
                if (pattern.equals("d-MMM")) {
                    // For patterns like "24-Sep", assume current year
                    MonthDay monthDay = MonthDay.parse(dateStr, DateTimeFormatter.ofPattern("d-MMM"));
                    return monthDay.atYear(LocalDate.now().getYear());
                } else {
                    return LocalDate.parse(dateStr, formatter);
                }
            } catch (DateTimeParseException e) {
                // Try next pattern
                continue;
            }
        }
        
        logger.debug("Could not parse date: {}", dateStr);
        return null;
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
                        newLocation.setDefaultLocation(true);
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