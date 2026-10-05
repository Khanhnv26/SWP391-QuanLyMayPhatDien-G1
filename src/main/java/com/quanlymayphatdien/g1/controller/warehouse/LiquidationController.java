package com.quanlymayphatdien.g1.controller.warehouse;

import com.quanlymayphatdien.g1.dal.CategoryDAO;
import com.quanlymayphatdien.g1.dal.LiquidationDAO;
import com.quanlymayphatdien.g1.dal.LiquidationDetailDAO;
import com.quanlymayphatdien.g1.dal.PurchaseOrderDAO;
import com.quanlymayphatdien.g1.dal.ReceiptDAO;
import com.quanlymayphatdien.g1.dal.ReceiptDetailDAO;
import com.quanlymayphatdien.g1.dal.InventoryDAO;
import com.quanlymayphatdien.g1.dal.UserDAO;
import com.quanlymayphatdien.g1.dal.WarehouseDAO;
import com.quanlymayphatdien.g1.dal.CustomerDAO;
import com.quanlymayphatdien.g1.dal.ActivityLogDAO;
import com.quanlymayphatdien.g1.entity.ActivityLog;
import com.quanlymayphatdien.g1.entity.Category;
import com.quanlymayphatdien.g1.entity.Customer;
import com.quanlymayphatdien.g1.entity.Inventory;
import com.quanlymayphatdien.g1.entity.Liquidation;
import com.quanlymayphatdien.g1.entity.LiquidationDetail;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.quanlymayphatdien.g1.dal.GeneratorDAO;
import com.quanlymayphatdien.g1.entity.Generator;
import com.quanlymayphatdien.g1.entity.Receipt;
import com.quanlymayphatdien.g1.entity.ReceiptDetail;
import com.quanlymayphatdien.g1.entity.User;
import com.quanlymayphatdien.g1.entity.Warehouse;
import com.quanlymayphatdien.g1.utils.GlobalUtils;
import com.quanlymayphatdien.g1.utils.NotificationUtil;
import com.quanlymayphatdien.g1.utils.WarehouseAccessUtil;
import java.util.Collections;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import static java.net.URLEncoder.encode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@WebServlet(name = "LiquidationController", urlPatterns = {"/liquidations"})
@MultipartConfig
public class LiquidationController extends HttpServlet {

    private final LiquidationDAO liquidationDAO = new LiquidationDAO();
    private final LiquidationDetailDAO detailDAO = new LiquidationDetailDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ReceiptDAO receiptDAO = new ReceiptDAO();
    private final ReceiptDetailDAO receiptDetailDAO = new ReceiptDetailDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final UserDAO userDAO = new UserDAO();
    private final WarehouseDAO warehouseDAO = new WarehouseDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAO();
    private final PurchaseOrderDAO purchaseOrderDAO = new PurchaseOrderDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loggedUser") == null) {
            response.sendRedirect(request.getContextPath() + "/authen?action=login");
            return;
        }

        Set<String> perms = (Set<String>) session.getAttribute("userPermissions");
        if (perms == null || !perms.contains("liquidations.view")) {
            request.setAttribute("requiredPerm", "liquidations.view");
            request.getRequestDispatcher("/view/error/403.jsp").forward(request, response);
            return;
        }
        String action = request.getParameter("action");
        if (action == null) {
            action = "list";
        }

        try {
            switch (action) {
                case "create":
                    showCreateView(request, response);
                    break;
                case "edit_view":
                    if (!perms.contains("liquidations.create")) {
                        request.setAttribute("requiredPerm", "liquidations.create");
                        request.getRequestDispatcher("/view/error/403.jsp").forward(request, response);
                        return;
                    }
                    showEditView(request, response);
                    break;
                case "detail":
                    showDetail(request, response);
                    break;
                case "search_customer":
                    searchCustomerJson(request, response);
                    break;
                case "list":
                default:
                    showList(request, response);
                    break;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            String act = request.getParameter("action");
            boolean isAjax = "get_serials".equals(act) || "get_serials_all".equals(act) || "search_customer".equals(act);
            if (isAjax && !response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                response.getWriter().write("{\"error\":\"" + msg.replace("\"", "\\\"") + "\"}");
            }
        }
    }

    // Hiển thị màn tạo đơn
    private void showCreateView(HttpServletRequest request, HttpServletResponse response) throws Exception {
        HttpSession session = request.getSession(false);
        int scopedWarehouseId = WarehouseAccessUtil.getScopedWarehouseId(session);

        request.setAttribute("reasons", categoryDAO.findByType("liquidation_reason"));
        request.setAttribute("customerTypes", categoryDAO.findByType("customer_type"));

        if (scopedWarehouseId > 0) {
            Warehouse scoped = warehouseDAO.findById(scopedWarehouseId);
            request.setAttribute("warehouses", scoped != null ? Collections.singletonList(scoped) : Collections.emptyList());
            request.setAttribute("scopedWarehouseId", scopedWarehouseId);
        } else {
            request.setAttribute("warehouses", warehouseDAO.findAll());
        }

        Integer warehouseId = null;
        if (scopedWarehouseId > 0) {
            warehouseId = scopedWarehouseId;
        } else {
            String whParam = request.getParameter("warehouseId");
            if (whParam != null && !whParam.trim().isEmpty()) {
                try {
                    warehouseId = Integer.parseInt(whParam.trim());
                } catch (NumberFormatException ignore) {
                }
            }
        }
        request.setAttribute("selectedWarehouseId", warehouseId);

        String reasonParam = request.getParameter("reasonId");
        if (reasonParam != null && !reasonParam.trim().isEmpty()) {
            request.setAttribute("selectedReasonId", reasonParam.trim());
        }

        String condFilter = request.getParameter("cond");
        request.setAttribute("condFilter", condFilter != null ? condFilter : "all");

        String pageParam = request.getParameter("page");
        int page = 1;
        if (pageParam != null && !pageParam.trim().isEmpty()) {
            try {
                page = Integer.parseInt(pageParam.trim());
            } catch (NumberFormatException ignore) {
            }
        }
        if (page < 1) {
            page = 1;
        }
        int pageSize = 20;

        if (warehouseId != null) {
            Map<String, Integer> condCounts = inventoryDAO.countEligibleByCondition(warehouseId, 6);
            int cGood = condCounts.getOrDefault("GOOD", 0);
            int cPoor = condCounts.getOrDefault("POOR", 0);
            int cDamaged = condCounts.getOrDefault("DAMAGED", 0);
            int cAll = cGood + cPoor + cDamaged;
            request.setAttribute("condCountGood", cGood);
            request.setAttribute("condCountPoor", cPoor);
            request.setAttribute("condCountDamaged", cDamaged);
            request.setAttribute("condCountAll", cAll);

            String condSql = (condFilter != null && !"all".equals(condFilter)) ? condFilter : null;
            List<Inventory> inStock = inventoryDAO.findEligibleForLiquidation(warehouseId, 6, condSql, page, pageSize);
            int totalItems = inventoryDAO.countEligibleForLiquidation(warehouseId, 6, condSql);

            request.setAttribute("pickRows", buildPickRows(inStock, java.util.Collections.emptySet()));

            List<Map<String, Object>> locked = inventoryDAO.findPendingLiquidationSerialsByWarehouse(warehouseId);
            List<Map<String, Object>> lockedRows = new ArrayList<>();
            for (Map<String, Object> m : locked) {
                Map<String, Object> r = new HashMap<>();
                r.put("serialNumber", m.get("serialNumber"));
                r.put("model", m.get("generatorModel"));
                r.put("liquidationId", m.get("liquidationId"));
                r.put("liquidationCode", m.get("liquidationCode"));
                lockedRows.add(r);
            }
            request.setAttribute("lockedRows", lockedRows);

            int totalPages = (int) Math.ceil((double) totalItems / pageSize);
            request.setAttribute("currentPage", page);
            request.setAttribute("pageSize", pageSize);
            request.setAttribute("totalItems", totalItems);
            request.setAttribute("totalPages", totalPages);
        }

        User loggedUser = (User) request.getSession().getAttribute("loggedUser");
        request.setAttribute("currentUserName", loggedUser != null ? loggedUser.getName() : "");
        request.setAttribute("currentDateStr", java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        request.getRequestDispatcher("/view/liquidation/liquidation-create.jsp").forward(request, response);
    }

    // Build danh sách máy phẳng để render bảng, giữ nguyên thứ tự của inStock
    private List<Map<String, Object>> buildPickRows(List<Inventory> inStock, Set<String> selectedSerials) {
        java.time.format.DateTimeFormatter df = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Map<Integer, BigDecimal> priceCache = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (Inventory inv : inStock) {
            int gid = inv.getGeneratorId();
            BigDecimal price = priceCache.get(gid);
            if (price == null && !priceCache.containsKey(gid)) {
                try {
                    price = purchaseOrderDAO.findApprovedUnitPriceByGenerator(gid);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    price = null;
                }
                priceCache.put(gid, price);
            }
            String ageString = "";
            if (inv.getCreatedAt() != null) {
                ageString = computeAgeString(inv.getCreatedAt(), now);
            }
            Map<String, Object> r = new HashMap<>();
            r.put("serialNumber", inv.getSerialNumber());
            r.put("generatorId", gid);
            r.put("model", inv.getGeneratorModel());
            r.put("unitPrice", price != null ? price : BigDecimal.ZERO);
            r.put("condition", inv.getCondition());
            r.put("createdAtStr", inv.getCreatedAt() != null ? inv.getCreatedAt().format(df) : "");
            r.put("ageString", ageString);
            r.put("selected", selectedSerials.contains(inv.getSerialNumber()));
            rows.add(r);
        }
        return rows;
    }

    private String computeAgeString(java.time.LocalDateTime from, java.time.LocalDateTime to) {
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
        if (totalDays < 0) {
            return "0 ngày";
        }
        long years = totalDays / 365;
        long remainingAfterYears = totalDays % 365;
        long months = remainingAfterYears / 30;
        long days = remainingAfterYears % 30;

        if (years > 0) {
            if (months > 0) {
                return years + " năm " + months + " tháng";
            }
            return years + " năm";
        }
        if (months > 0) {
            if (days > 0) {
                return months + " tháng " + days + " ngày";
            }
            return months + " tháng";
        }
        return days + " ngày";
    }

    

    private void searchCustomerJson(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String q = request.getParameter("q");
        if (q == null) {
            q = "";
        }
        List<Customer> customers = customerDAO.findByFilters(q.trim(), "active", null, 1, 20);

        JsonArray arr = new JsonArray();
        for (Customer c : customers) {
            JsonObject o = new JsonObject();
            o.addProperty("id", c.getId());
            o.addProperty("name", c.getName());
            o.addProperty("phone", c.getPhone());
            o.addProperty("email", c.getEmail() != null ? c.getEmail() : "");
            o.addProperty("address", c.getAddress() != null ? c.getAddress() : "");
            o.addProperty("companyName", c.getCompanyName() != null ? c.getCompanyName() : "");
            arr.add(o);
        }
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(new Gson().toJson(arr));
    }

    private void showList(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String search = request.getParameter("search");
        String statusFilter = request.getParameter("status");
        int page = 1;
        int limit = 10;
        try {
            if (request.getParameter("page") != null) {
                page = Integer.parseInt(request.getParameter("page"));
            }
        } catch (NumberFormatException e) {
            page = 1;
        }
        int offset = (page - 1) * limit;

        HttpSession session = request.getSession(false);
        java.util.Set<String> perms = session != null ? (java.util.Set<String>) session.getAttribute("userPermissions") : null;
        User currentUser = session != null ? (User) session.getAttribute("loggedUser") : null;

        Integer filterUserId = null;
        if (perms != null && !perms.contains("liquidations.approve_ceo")) {
            if (currentUser != null) {
                filterUserId = currentUser.getId();
            }
        }

        int totalRecords = liquidationDAO.countTotal(search, statusFilter, filterUserId);
        int totalPages = (int) Math.ceil((double) totalRecords / limit);

        List<Liquidation> list = liquidationDAO.findWithPagination(limit, offset, search, statusFilter, filterUserId);

        java.util.Map<String, Integer> kpis = liquidationDAO.getKpiCounts(filterUserId);
        int kpiPendingCeo = kpis.getOrDefault(GlobalUtils.LIQUIDATION_STATUS_PENDING_CEO, 0);
        int kpiApproved = kpis.getOrDefault(GlobalUtils.LIQUIDATION_STATUS_APPROVED, 0);
        int kpiCompleted = kpis.getOrDefault(GlobalUtils.LIQUIDATION_STATUS_COMPLETED, 0);
        int kpiRequestEdit = kpis.getOrDefault(GlobalUtils.LIQUIDATION_STATUS_CEO_REQUEST_EDIT, 0);
        int kpiRejected = kpis.getOrDefault(GlobalUtils.LIQUIDATION_STATUS_CANCELLED, 0);

        request.setAttribute("kpiPendingCeo", kpiPendingCeo);
        request.setAttribute("kpiApproved", kpiApproved);
        request.setAttribute("kpiCompleted", kpiCompleted);
        request.setAttribute("kpiRequestEdit", kpiRequestEdit);
        request.setAttribute("kpiRejected", kpiRejected);

        request.setAttribute("liquidations", list);
        request.setAttribute("currentPage", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("search", search);
        request.setAttribute("statusFilter", statusFilter);

        request.getRequestDispatcher("/view/liquidation/liquidation-list.jsp").forward(request, response);
    }

    private void showDetail(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String idStr = request.getParameter("id");
        if (idStr == null || idStr.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/liquidations");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(idStr.trim());
        } catch (NumberFormatException ex) {
            response.sendRedirect(request.getContextPath() + "/liquidations");
            return;
        }

        Liquidation l = liquidationDAO.findById(id);
        request.setAttribute("liquidation", l);

        List<LiquidationDetail> details = detailDAO.findByLiquidationId(id);
        request.setAttribute("details", details);

        List<Category> ceoRejectFeedbacks = categoryDAO.findByType("ceo_reject_reason");
        List<Category> ceoEditFeedbacks = categoryDAO.findByType("ceo_request_edit_reason");
        request.setAttribute("ceoRejectFeedbacks", ceoRejectFeedbacks);
        request.setAttribute("ceoEditFeedbacks", ceoEditFeedbacks);

        request.setAttribute("customers", customerDAO.findAll());

        HttpSession session = request.getSession(false);
        if (session != null) {
            Set<String> perms = (Set<String>) session.getAttribute("userPermissions");
            boolean isCeo = perms != null && perms.contains("liquidations.approve_ceo");
            boolean isStaff = perms != null && perms.contains("liquidations.create");
            request.setAttribute("isCeo", isCeo);
            request.setAttribute("isStaff", isStaff);
        }

        List<ActivityLog> history = activityLogDAO.findByEntityTypeAndId("liquidation", id, 1, 100);
        int totalHistory = activityLogDAO.countByEntityTypeAndId("liquidation", id);
        request.setAttribute("liquidationHistory", history);
        request.setAttribute("totalHistory", totalHistory);

        request.setAttribute("customerTypes", categoryDAO.findByType("customer_type"));

       
        String st = l.getStatus();
        boolean isEditMode = GlobalUtils.LIQUIDATION_STATUS_CEO_REQUEST_EDIT.equals(st);
        request.setAttribute("isEditMode", isEditMode);

        boolean isApproved = GlobalUtils.LIQUIDATION_STATUS_APPROVED.equals(st);
        request.setAttribute("isApproved", isApproved);

        if (isEditMode) {
            request.setAttribute("warehouses", warehouseDAO.findAll());
            request.setAttribute("reasons", categoryDAO.findByType("liquidation_reason"));
            request.setAttribute("generators", new GeneratorDAO().findAll());

            int orderWhId = l.getWarehouseId();
            Integer paramWh = null;
            String whParam = request.getParameter("warehouseId");
            if (whParam != null && !whParam.trim().isEmpty()) {
                try {
                    paramWh = Integer.parseInt(whParam.trim());
                } catch (NumberFormatException ignore) {}
            }
            int whId = (paramWh != null) ? paramWh : orderWhId;
            boolean sameWarehouse = (whId == orderWhId);
            request.setAttribute("selectedWarehouseId", whId);

            // Existing selected serials (only if same warehouse)
            Set<String> selectedSerials = new LinkedHashSet<>();
            if (sameWarehouse) {
                for (LiquidationDetail d : details) {
                    selectedSerials.add(d.getSerialNumber());
                }
            }
            String pageParam = request.getParameter("page");
            int page = 1;
            if (pageParam != null && !pageParam.trim().isEmpty()) {
                try { page = Integer.parseInt(pageParam.trim()); } catch (NumberFormatException ignore) {}
            }
            if (page < 1) page = 1;
            int pageSize = 6;

            String condParam = request.getParameter("cond");
            String condSqlFilter = (condParam != null && !condParam.trim().isEmpty()) ? condParam.trim() : "all";
            String condSql = !"all".equals(condSqlFilter) ? condSqlFilter : null;
            request.setAttribute("condFilter", condSqlFilter);

            Map<String, Integer> condCounts = inventoryDAO.countEligibleByCondition(whId, 6);
            int cGood = condCounts.getOrDefault("GOOD", 0);
            int cPoor = condCounts.getOrDefault("POOR", 0);
            int cDamaged = condCounts.getOrDefault("DAMAGED", 0);
            int cAll = cGood + cPoor + cDamaged;
            request.setAttribute("condCountGood", cGood);
            request.setAttribute("condCountPoor", cPoor);
            request.setAttribute("condCountDamaged", cDamaged);
            request.setAttribute("condCountAll", cAll);

            int totalItems = inventoryDAO.countEligibleForLiquidation(whId, 6, condSql);
            int totalPages = (int) Math.ceil((double) totalItems / pageSize);
            request.setAttribute("currentPage", page);
            request.setAttribute("pageSize", pageSize);
            request.setAttribute("totalItems", totalItems);
            request.setAttribute("totalPages", totalPages);

            List<Inventory> inStock = inventoryDAO.findEligibleForLiquidation(whId, 6, condSql, page, pageSize);
            List<Map<String, Object>> pickRows = buildPickRows(inStock, selectedSerials);

    
            if (sameWarehouse) {
                Set<String> inStockSerials = new LinkedHashSet<>();
                for (Inventory inv : inStock) {
                    inStockSerials.add(inv.getSerialNumber());
                }
                List<String> missingSerials = new ArrayList<>();
                for (LiquidationDetail d : details) {
                    if (!inStockSerials.contains(d.getSerialNumber())) {
                        missingSerials.add(d.getSerialNumber());
                    }
                }
                Map<String, String> condBySerial = inventoryDAO.findLatestConditionBySerials(missingSerials);
                java.time.format.DateTimeFormatter dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                for (LiquidationDetail d : details) {
                    if (!inStockSerials.contains(d.getSerialNumber())) {
                        BigDecimal price;
                        try {
                            price = purchaseOrderDAO.findApprovedUnitPriceByGenerator(d.getGeneratorId());
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            price = null;
                        }
                        String createdAtStr = "";
                        try {
                            Inventory inv = inventoryDAO.findBySerialNumber(d.getSerialNumber());
                            if (inv != null && inv.getCreatedAt() != null) {
                                createdAtStr = inv.getCreatedAt().format(dateFmt);
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                        Map<String, Object> r = new HashMap<>();
                        r.put("serialNumber", d.getSerialNumber());
                        r.put("generatorId", d.getGeneratorId());
                        r.put("model", d.getGeneratorModelName());
                        r.put("unitPrice", price != null ? price : BigDecimal.ZERO);
                        r.put("condition", condBySerial.get(d.getSerialNumber()));
                        r.put("createdAtStr", createdAtStr);
                        r.put("selected", true);
                        pickRows.add(r);
                    }
                }
            }

    
            Map<String, BigDecimal> liqPriceBySerial = new HashMap<>();
            for (LiquidationDetail d : details) {
                liqPriceBySerial.put(d.getSerialNumber(), d.getLiquidationPrice());
            }
            for (Map<String, Object> r : pickRows) {
                Object sn = r.get("serialNumber");
                BigDecimal lp = (sn != null) ? liqPriceBySerial.get(sn.toString()) : null;
                r.put("liquidationPrice", lp != null ? lp : BigDecimal.ZERO);
            }
            request.setAttribute("pickRows", pickRows);
        }

        request.getRequestDispatcher("/view/liquidation/liquidation-detail.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        HttpSession session = request.getSession();
        User currentUser = (User) session.getAttribute("loggedUser");

        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/authen?action=login");
            return;
        }

        Set<String> perms = (Set<String>) session.getAttribute("userPermissions");
        if (perms == null) {
            response.sendRedirect(request.getContextPath() + "/authen?action=login");
            return;
        }

        try {
            switch (action) {
                case "create_customer":
                    if (!perms.contains("liquidations.create")) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN);
                        return;
                    }
                    handleCreateCustomerAjax(request, response, currentUser);
                    return;
                case "create":
                    if (!perms.contains("liquidations.create")) {
                        request.setAttribute("requiredPerm", "liquidations.create");
                        request.getRequestDispatcher("/view/error/403.jsp").forward(request, response);
                        return;
                    }
                    handleCreate(request, response, currentUser);
                    break;
                case "edit_submit":
                    if (!perms.contains("liquidations.create")) {
                        request.setAttribute("requiredPerm", "liquidations.create");
                        request.getRequestDispatcher("/view/error/403.jsp").forward(request, response);
                        return;
                    }
                    handleEditSubmit(request, response, currentUser);
                    break;
                case "approve_ceo":
                    if (!perms.contains("liquidations.approve_ceo")) {
                        request.setAttribute("requiredPerm", "liquidations.approve_ceo");
                        request.getRequestDispatcher("/view/error/403.jsp").forward(request, response);
                        return;
                    }
                    handleCEOApprove(request, response, currentUser);
                    break;
                case "reject_ceo":
                    if (!perms.contains("liquidations.approve_ceo")) {
                        request.setAttribute("requiredPerm", "liquidations.approve_ceo");
                        request.getRequestDispatcher("/view/error/403.jsp").forward(request, response);
                        return;
                    }
                    handleCEOReject(request, response, currentUser, true);
                    break;
                case "request_edit_ceo":
                    if (!perms.contains("liquidations.approve_ceo")) {
                        request.setAttribute("requiredPerm", "liquidations.approve_ceo");
                        request.getRequestDispatcher("/view/error/403.jsp").forward(request, response);
                        return;
                    }
                    handleCEOReject(request, response, currentUser, false);
                    break;

            }
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/liquidations?error=1");
        }
    }

    private void handleCreateCustomerAjax(HttpServletRequest request, HttpServletResponse response, User user) throws Exception {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        Gson gson = new Gson();
        try {
            String name = request.getParameter("custName");
            String phone = request.getParameter("custPhone");
            String email = request.getParameter("custEmail");
            String address = request.getParameter("custAddress");
            String companyName = request.getParameter("custCompanyName");
            String typeIdStr = request.getParameter("custTypeId");

            if (name == null || name.trim().isEmpty() || phone == null || phone.trim().isEmpty()) {
                JsonObject err = new JsonObject();
                err.addProperty("success", false);
                err.addProperty("error", "Họ tên và SĐT là bắt buộc");
                out.write(gson.toJson(err));
                return;
            }
            if (customerDAO.isPhoneExists(phone.trim(), null)) {
                Customer existing = customerDAO.findByPhone(phone.trim());
                if (existing != null) {
                    out.write(gson.toJson(customerToJson(existing, true)));
                    return;
                }
            }
            Customer c = new Customer();
            c.setName(name.trim());
            c.setPhone(phone.trim());
            c.setEmail(email != null && !email.trim().isEmpty() ? email.trim() : null);
            c.setAddress(address != null && !address.trim().isEmpty() ? address.trim() : null);
            c.setCompanyName(companyName != null && !companyName.trim().isEmpty() ? companyName.trim() : null);
            if (typeIdStr != null && !typeIdStr.isEmpty()) {
                try {
                    c.setCustomerTypeId(Integer.parseInt(typeIdStr));
                } catch (NumberFormatException ignore) {
                }
            }
            c.setStatus("active");
            c.setCreatedBy(user.getId());
            int newId = customerDAO.insert(c);
            if (newId > 0) {
                c.setId(newId);
                out.write(gson.toJson(customerToJson(c, false)));
            } else {
                JsonObject err = new JsonObject();
                err.addProperty("success", false);
                err.addProperty("error", "Không thể lưu khách hàng");
                out.write(gson.toJson(err));
            }
        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.addProperty("success", false);
            err.addProperty("error", e.getMessage() != null ? e.getMessage() : "");
            out.write(gson.toJson(err));
        }
    }

    private JsonObject customerToJson(Customer c, boolean existing) {
        JsonObject o = new JsonObject();
        o.addProperty("success", true);
        o.addProperty("existing", existing);
        o.addProperty("id", c.getId());
        o.addProperty("name", c.getName());
        o.addProperty("phone", c.getPhone());
        o.addProperty("email", c.getEmail() != null ? c.getEmail() : "");
        o.addProperty("address", c.getAddress() != null ? c.getAddress() : "");
        o.addProperty("companyName", c.getCompanyName() != null ? c.getCompanyName() : "");
        return o;
    }

    private void handleCreate(HttpServletRequest request, HttpServletResponse response, User user) throws Exception {
        HttpSession session = request.getSession(false);
        int scopedWarehouseId = WarehouseAccessUtil.getScopedWarehouseId(session);

        int reasonId = Integer.parseInt(request.getParameter("reasonId"));
        int warehouseId = Integer.parseInt(request.getParameter("warehouseId"));

        if (scopedWarehouseId > 0 && scopedWarehouseId != warehouseId) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String[] generatorIds = request.getParameterValues("generatorId");
        String[] serialNumbers = request.getParameterValues("serialNumber");
        String[] liquidationPrices = request.getParameterValues("liquidationPrice");

        if (generatorIds == null || serialNumbers == null || generatorIds.length == 0) {
            response.sendRedirect(request.getContextPath() + "/liquidations?error=" + encode("Phải chọn ít nhất 1 máy", "UTF-8"));
            return;
        }

        if (generatorIds.length != serialNumbers.length
                || liquidationPrices == null || liquidationPrices.length != generatorIds.length) {
            response.sendRedirect(request.getContextPath() + "/liquidations?error=" + encode("Dữ liệu không hợp lệ", "UTF-8"));
            return;
        }

        // Parse + validate giá thanh lý từng máy (bắt buộc > 0)
        BigDecimal[] parsedPrices = new BigDecimal[liquidationPrices.length];
        for (int i = 0; i < liquidationPrices.length; i++) {
            String priceStr = liquidationPrices[i] == null ? "" : liquidationPrices[i].replaceAll("[^0-9]", "").trim();
            if (priceStr.isEmpty()) {
                response.sendRedirect(request.getContextPath() + "/liquidations?action=create&error="
                        + encode("Phải nhập giá thanh lý cho tất cả máy đã chọn", "UTF-8"));
                return;
            }
            BigDecimal price = new BigDecimal(priceStr);
            if (price.signum() <= 0) {
                response.sendRedirect(request.getContextPath() + "/liquidations?action=create&error="
                        + encode("Giá thanh lý phải lớn hơn 0", "UTF-8"));
                return;
            }
            parsedPrices[i] = price;
        }

        Set<String> uniqueSerials = new LinkedHashSet<>();
        for (String sn : serialNumbers) {
            uniqueSerials.add(sn);
        }
        if (uniqueSerials.size() != serialNumbers.length) {
            response.sendRedirect(request.getContextPath() + "/liquidations?error=" + encode("Có số serial trùng trong phiếu", "UTF-8"));
            return;
        }

        // Resolve/tạo khách hàng — bắt buộc trước khi gửi Sếp duyệt
        Integer resolvedCustomerId = resolveCustomerFromRequest(request, user);
        if (resolvedCustomerId == null) {
            response.sendRedirect(request.getContextPath() + "/liquidations?action=create&error="
                    + encode("Phải chọn hoặc nhập Khách hàng (Tên + SĐT) trước khi gửi Sếp duyệt", "UTF-8"));
            return;
        }

        List<String> serialList = new ArrayList<>(uniqueSerials);

        java.time.LocalDateTime cutoffDate = java.time.LocalDateTime.now().minusMonths(6);
        java.time.format.DateTimeFormatter ageFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (String sn : serialList) {
            Inventory inv = inventoryDAO.findBySerialNumber(sn);
            if (inv == null) {
                response.sendRedirect(request.getContextPath() + "/liquidations?action=create&error="
                        + java.net.URLEncoder.encode("Số serial \"" + sn + "\" không tồn tại trong hệ thống", "UTF-8"));
                return;
            }
            if (inv.getCondition() == null || inv.getCondition().isEmpty()) {
                response.sendRedirect(request.getContextPath() + "/liquidations?action=create&error="
                        + java.net.URLEncoder.encode("Số serial \"" + sn + "\" chưa được kiểm kê, không thể thanh lý", "UTF-8"));
                return;
            }
            if ("GOOD".equals(inv.getCondition()) && inv.getCreatedAt() != null && inv.getCreatedAt().isAfter(cutoffDate)) {
                String ageStr = inv.getCreatedAt().format(ageFmt);
                response.sendRedirect(request.getContextPath() + "/liquidations?action=create&error="
                        + java.net.URLEncoder.encode("Số serial \"" + sn + "\" là máy tốt mới nhập kho (" + ageStr + "), chưa đủ 6 tháng để thanh lý", "UTF-8"));
                return;
            }
        }

        //chỉ thành công khi tất cả serial đang IN_STOCK ở đúng warehouse.
        Connection conn = null;
        try {
            conn = inventoryDAO.getConnection();
            conn.setAutoCommit(false);

            int affected = inventoryDAO.claimInStockBatch(conn, serialList, warehouseId, GlobalUtils.INVENTORY_STATUS_PENDING_LIQUIDATION);
            if (affected != serialList.size()) {
                List<String> bad = inventoryDAO.findUnavailableSerials(conn, serialList, warehouseId);
                conn.rollback();
                String msg = "Không tạo được phiếu — các số serial sau đã bị giữ chỗ hoặc không thuộc kho này: "
                        + String.join(", ", bad);
                response.sendRedirect(request.getContextPath() + "/liquidations?action=create&error="
                        + java.net.URLEncoder.encode(msg, "UTF-8"));
                return;
            }

            // Quản lý kho tạo đơn đã kèm giá + khách hàng → gửi thẳng CEO duyệt.
            Liquidation l = new Liquidation();
            l.setLiquidationCode("LIQ" + System.currentTimeMillis());
            l.setCreatedBy(user.getId());
            l.setReasonId(reasonId);
            l.setWarehouseId(warehouseId);
            l.setStatus(GlobalUtils.LIQUIDATION_STATUS_PENDING_CEO);
            l.setCustomerId(resolvedCustomerId);

            int insertedId = liquidationDAO.insert(l);
            if (insertedId <= 0) {
                conn.rollback();
                response.sendRedirect(request.getContextPath() + "/liquidations?error=" + java.net.URLEncoder.encode("Không lưu được phiếu thanh lý", "UTF-8"));
                return;
            }

            for (int i = 0; i < generatorIds.length; i++) {
                LiquidationDetail d = new LiquidationDetail();
                d.setLiquidationId(insertedId);
                d.setGeneratorId(Integer.parseInt(generatorIds[i]));
                d.setSerialNumber(serialNumbers[i]);
                BigDecimal poPrice = purchaseOrderDAO.findApprovedUnitPriceByGenerator(d.getGeneratorId());
                d.setOriginalPrice(poPrice != null ? poPrice : BigDecimal.ZERO);
                d.setLiquidationPrice(parsedPrices[i]);
                if (detailDAO.insert(d) <= 0) {
                    throw new Exception("Không lưu được dòng chi tiết cho số serial " + serialNumbers[i]);
                }
            }

            conn.commit();

            // Đơn đi thẳng CEO → thông báo các CEO
            List<User> ceos = userDAO.findUsersByPermission("liquidations", "approve_ceo");
            for (User ceo : ceos) {
                NotificationUtil.send(
                        ceo.getId(),
                        "Đơn thanh lý " + l.getLiquidationCode() + " — chờ CEO duyệt",
                        "Quản lý " + user.getName() + " đã tạo đơn thanh lý " + l.getLiquidationCode() + " và gửi lên CEO duyệt.",
                        request.getContextPath() + "/liquidations?action=detail&id=" + insertedId,
                        "liquidation",
                        insertedId
                );
            }

            ActivityLog log = new ActivityLog();
            log.setUserId(user.getId());
            log.setEntityType("liquidation");
            log.setAction("CREATE");
            log.setEntityId(insertedId);
            log.setEntityName(l.getLiquidationCode());
            log.setDetails("Quản lý kho tạo đơn thanh lý, báo giá & gửi CEO duyệt: " + l.getLiquidationCode());
            activityLogDAO.insert(log);

            request.getSession().setAttribute("toastMessage", "Tạo đơn thanh lý thành công, chờ CEO duyệt");
            request.getSession().setAttribute("toastType", "success");
            response.sendRedirect(request.getContextPath() + "/liquidations");
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception ignored) {
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    // Resolve khách hàng từ request: ưu tiên customerId có sẵn, nếu không thì khớp theo SĐT
    // hoặc tạo mới từ (tên + SĐT + ...). Trả về null nếu không có đủ thông tin.
    private Integer resolveCustomerFromRequest(HttpServletRequest request, User user) throws Exception {
        String customerIdStr = request.getParameter("customerId");
        if (customerIdStr != null && !customerIdStr.trim().isEmpty()) {
            try {
                return Integer.parseInt(customerIdStr.trim());
            } catch (NumberFormatException ignore) {
            }
        }

        String custName = request.getParameter("customerName");
        String custPhone = request.getParameter("customerPhone");
        if (custName == null || custName.trim().isEmpty()
                || custPhone == null || custPhone.trim().isEmpty()) {
            return null;
        }

        if (customerDAO.isPhoneExists(custPhone.trim(), null)) {
            Customer existing = customerDAO.findByPhone(custPhone.trim());
            if (existing != null) {
                return existing.getId();
            }
        }

        Customer c = new Customer();
        c.setName(custName.trim());
        c.setPhone(custPhone.trim());
        String email = request.getParameter("customerEmail");
        String address = request.getParameter("customerAddress");
        String company = request.getParameter("customerCompany");
        String typeIdStr = request.getParameter("customerTypeId");
        c.setEmail(email != null && !email.trim().isEmpty() ? email.trim() : null);
        c.setAddress(address != null && !address.trim().isEmpty() ? address.trim() : null);
        c.setCompanyName(company != null && !company.trim().isEmpty() ? company.trim() : null);
        if (typeIdStr != null && !typeIdStr.trim().isEmpty()) {
            try {
                c.setCustomerTypeId(Integer.parseInt(typeIdStr.trim()));
            } catch (NumberFormatException ignore) {
            }
        }
        c.setStatus("active");
        c.setCreatedBy(user.getId());
        int newId = customerDAO.insert(c);
        return newId > 0 ? newId : null;
    }

    private void handleCEOApprove(HttpServletRequest request, HttpServletResponse response, User user) throws Exception {
        int liquidationId = Integer.parseInt(request.getParameter("liquidationId"));
        Liquidation l = liquidationDAO.findById(liquidationId);
        if (l == null) {
            response.sendRedirect(request.getContextPath() + "/liquidations");
            return;
        }

        List<LiquidationDetail> details = detailDAO.findByLiquidationId(liquidationId);
        if (details.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/liquidations?action=detail&id=" + liquidationId
                    + "&error=" + encode("Đơn thanh lý không có dòng chi tiết", "UTF-8"));
            return;
        }

        liquidationDAO.updateStatus(liquidationId, GlobalUtils.LIQUIDATION_STATUS_APPROVED, user.getId(), null);

        NotificationUtil.send(
                l.getCreatedBy(),
                "Đơn thanh lý " + l.getLiquidationCode() + " — CEO đã duyệt",
                "CEO " + user.getName() + " đã duyệt đơn thanh lý " + l.getLiquidationCode() + ". Hãy tạo phiếu xuất kho.",
                request.getContextPath() + "/liquidations?action=detail&id=" + liquidationId,
                "liquidation",
                liquidationId
        );

        ActivityLog liqLog = new ActivityLog();
        liqLog.setUserId(user.getId());
        liqLog.setEntityType("liquidation");
        liqLog.setAction("CEO_APPROVE");
        liqLog.setEntityId(liquidationId);
        liqLog.setEntityName(l.getLiquidationCode());
        liqLog.setDetails("CEO duyệt đơn thanh lý, chờ tạo phiếu xuất kho");
        activityLogDAO.insert(liqLog);

        request.getSession().setAttribute("toastMessage", "Đã duyệt đơn thanh lý");
        request.getSession().setAttribute("toastType", "success");
        response.sendRedirect(request.getContextPath() + "/liquidations?action=detail&id=" + liquidationId);
    }

    private void handleCEOReject(HttpServletRequest request, HttpServletResponse response, User user, boolean isPermanent) throws Exception {
        int liquidationId = Integer.parseInt(request.getParameter("liquidationId"));
        int feedbackId = Integer.parseInt(request.getParameter("ceoFeedbackId"));

        liquidationDAO.updateCeoReject(liquidationId, user.getId(), feedbackId, isPermanent);

        Liquidation l = liquidationDAO.findById(liquidationId);
        if (l == null) {
            response.sendRedirect(request.getContextPath() + "/liquidations");
            return;
        }
        if (isPermanent) {
            List<LiquidationDetail> details = detailDAO.findByLiquidationId(liquidationId);
            List<String> serials = new ArrayList<>();
            for (LiquidationDetail d : details) {
                serials.add(d.getSerialNumber());
            }
            if (!serials.isEmpty()) {
                Connection conn = null;
                try {
                    conn = inventoryDAO.getConnection();
                    conn.setAutoCommit(false);
                    inventoryDAO.updateStatusBatch(conn, serials, GlobalUtils.INVENTORY_STATUS_IN_STOCK);
                    conn.commit();
                } catch (Exception ex) {
                    if (conn != null) try { conn.rollback(); } catch (Exception ignored) {}
                    throw ex;
                } finally {
                    if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (Exception ignored) {}
                }
            }
        }

        NotificationUtil.send(
                l.getCreatedBy(),
                isPermanent ? "Đơn thanh lý " + l.getLiquidationCode() + " — CEO đã từ chối" : "Đơn thanh lý " + l.getLiquidationCode() + " — CEO yêu cầu sửa",
                isPermanent
                        ? "CEO " + user.getName() + " đã từ chối đơn thanh lý " + l.getLiquidationCode() + " và huỷ bỏ."
                        : "CEO " + user.getName() + " yêu cầu sửa đơn thanh lý " + l.getLiquidationCode() + ". Hãy kiểm tra feedback và chỉnh sửa.",
                request.getContextPath() + "/liquidations?action=detail&id=" + liquidationId,
                "liquidation",
                liquidationId
        );
        ActivityLog log = new ActivityLog();
        log.setUserId(user.getId());
        log.setEntityType("liquidation");
        log.setAction(isPermanent ? GlobalUtils.LIQUIDATION_STATUS_CANCELLED : GlobalUtils.LIQUIDATION_STATUS_CEO_REQUEST_EDIT);
        log.setEntityId(liquidationId);
        log.setEntityName(l.getLiquidationCode());
        log.setDetails(isPermanent ? "CEO từ chối và huỷ bỏ đơn thanh lý vĩnh viễn" : "CEO yêu cầu sửa đơn thanh lý");
        activityLogDAO.insert(log);

        String toastMsg = isPermanent ? "Đã từ chối và huỷ đơn thanh lý" : "Đã yêu cầu sửa đơn thanh lý";
        request.getSession().setAttribute("toastMessage", toastMsg);
        request.getSession().setAttribute("toastType", "success");
        response.sendRedirect(request.getContextPath() + "/liquidations?action=detail&id=" + liquidationId);
    }

    private void showEditView(HttpServletRequest request, HttpServletResponse response) throws Exception {
        // Redirect to detail page — edit is now inline on the detail page
        String idStr = request.getParameter("id");
        if (idStr == null || idStr.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/liquidations");
            return;
        }
        String whParam = request.getParameter("warehouseId");
        String redirect = request.getContextPath() + "/liquidations?action=detail&id=" + idStr.trim();
        if (whParam != null && !whParam.trim().isEmpty()) {
            redirect += "&warehouseId=" + whParam.trim();
        }
        response.sendRedirect(redirect);
    }

    private void handleEditSubmit(HttpServletRequest request, HttpServletResponse response, User user) throws Exception {
        int liquidationId = Integer.parseInt(request.getParameter("liquidationId"));
        int reasonId = Integer.parseInt(request.getParameter("reasonId"));
        int warehouseId = Integer.parseInt(request.getParameter("warehouseId"));
        String[] generatorIds = request.getParameterValues("generatorId");
        String[] serialNumbers = request.getParameterValues("serialNumber");
        String[] liquidationPrices = request.getParameterValues("liquidationPrice");

        Liquidation l = liquidationDAO.findById(liquidationId);
        if (l == null || !GlobalUtils.LIQUIDATION_STATUS_CEO_REQUEST_EDIT.equals(l.getStatus())) {
            response.sendRedirect(request.getContextPath() + "/liquidations");
            return;
        }

        if (generatorIds == null || serialNumbers == null || generatorIds.length == 0) {
            response.sendRedirect(request.getContextPath() + "/liquidations?action=detail&id=" + liquidationId
                    + "&error=" + java.net.URLEncoder.encode("Phải chọn ít nhất 1 máy", "UTF-8"));
            return;
        }

        if (generatorIds.length != serialNumbers.length
                || liquidationPrices == null || liquidationPrices.length != generatorIds.length) {
            response.sendRedirect(request.getContextPath() + "/liquidations?action=detail&id=" + liquidationId
                    + "&error=" + java.net.URLEncoder.encode("Dữ liệu không hợp lệ", "UTF-8"));
            return;
        }

        // Parse + validate giá thanh lý từng máy (bắt buộc > 0)
        BigDecimal[] parsedPrices = new BigDecimal[liquidationPrices.length];
        for (int i = 0; i < liquidationPrices.length; i++) {
            String priceStr = liquidationPrices[i] == null ? "" : liquidationPrices[i].replaceAll("[^0-9]", "").trim();
            if (priceStr.isEmpty()) {
                response.sendRedirect(request.getContextPath() + "/liquidations?action=edit_view&id=" + liquidationId
                        + "&error=" + java.net.URLEncoder.encode("Phải nhập giá thanh lý cho tất cả máy đã chọn", "UTF-8"));
                return;
            }
            BigDecimal price = new BigDecimal(priceStr);
            if (price.signum() <= 0) {
                response.sendRedirect(request.getContextPath() + "/liquidations?action=edit_view&id=" + liquidationId
                        + "&error=" + java.net.URLEncoder.encode("Giá thanh lý phải lớn hơn 0", "UTF-8"));
                return;
            }
            parsedPrices[i] = price;
        }

        Set<String> uniqueSerials = new LinkedHashSet<>();
        for (String sn : serialNumbers) {
            uniqueSerials.add(sn);
        }
        if (uniqueSerials.size() != serialNumbers.length) {
            response.sendRedirect(request.getContextPath() + "/liquidations?action=detail&id=" + liquidationId
                    + "&error=" + java.net.URLEncoder.encode("Có số serial trùng trong phiếu", "UTF-8"));
            return;
        }
        List<String> newSerialList = new ArrayList<>(uniqueSerials);

        // Load old details + validate giá thanh lý không đổi
        List<LiquidationDetail> oldDetails = detailDAO.findByLiquidationId(liquidationId);
        Map<String, BigDecimal> oldPriceBySerial = new HashMap<>();
        for (LiquidationDetail oldD : oldDetails) {
            if (oldD.getLiquidationPrice() != null)
                oldPriceBySerial.put(oldD.getSerialNumber(), oldD.getLiquidationPrice());
        }
        boolean allSame = true;
        for (int i = 0; i < serialNumbers.length; i++) {
            BigDecimal oldP = oldPriceBySerial.get(serialNumbers[i]);
            if (oldP == null || oldP.compareTo(parsedPrices[i]) != 0) {
                allSame = false;
                break;
            }
        }
        if (allSame) {
            response.sendRedirect(request.getContextPath() + "/liquidations?action=edit_view&id=" + liquidationId
                    + "&error=" + java.net.URLEncoder.encode("Giá thanh lý không thay đổi so với giá cũ. Vui lòng điều chỉnh giá trước khi gửi lại.", "UTF-8"));
            return;
        }

        Connection conn = null;
        try {
            conn = inventoryDAO.getConnection();
            conn.setAutoCommit(false);

            // Trả lại trạng thái cũ (PENDING_LIQUIDATION -> IN_STOCK) cho serials cũ
            List<String> oldSerials = new ArrayList<>();
            for (LiquidationDetail oldD : oldDetails) {
                oldSerials.add(oldD.getSerialNumber());
            }
            if (!oldSerials.isEmpty()) {
                inventoryDAO.updateStatusBatch(conn, oldSerials, GlobalUtils.INVENTORY_STATUS_IN_STOCK);
            }

            int affected = inventoryDAO.claimInStockBatch(conn, newSerialList, warehouseId, GlobalUtils.INVENTORY_STATUS_PENDING_LIQUIDATION);
            if (affected != newSerialList.size()) {
                List<String> bad = inventoryDAO.findUnavailableSerials(conn, newSerialList, warehouseId);
                conn.rollback();
                String msg = "Không lưu được — các serial sau đã bị giữ chỗ hoặc không thuộc kho này: "
                        + String.join(", ", bad);
                response.sendRedirect(request.getContextPath() + "/liquidations?action=edit_view&id=" + liquidationId
                        + "&error=" + java.net.URLEncoder.encode(msg, "UTF-8"));
                return;
            }

            String targetStatus = GlobalUtils.LIQUIDATION_STATUS_PENDING_CEO;
            boolean updated = liquidationDAO.updateReasonAndStatus(conn, liquidationId, reasonId, targetStatus);
            if (!updated) {
                conn.rollback();
                response.sendRedirect(request.getContextPath() + "/liquidations?action=detail&id=" + liquidationId);
                return;
            }

            String customerIdStr = request.getParameter("customerId");
            Integer newCustomerId = null;
            if (customerIdStr != null && !customerIdStr.trim().isEmpty()) {
                try {
                    newCustomerId = Integer.parseInt(customerIdStr.trim());
                } catch (NumberFormatException ignore) {
                }
            }
            Integer effectiveCustomerId = (newCustomerId != null) ? newCustomerId : l.getCustomerId();
            String updateWarehouseSql = "UPDATE liquidation SET warehouse_id = ?, customer_id = ? WHERE liquidation_id = ?";
            try (PreparedStatement p = conn.prepareStatement(updateWarehouseSql)) {
                p.setInt(1, warehouseId);
                if (effectiveCustomerId != null) {
                    p.setInt(2, effectiveCustomerId);
                } else {
                    p.setNull(2, java.sql.Types.INTEGER);
                }
                p.setInt(3, liquidationId);
                p.executeUpdate();
            }

            detailDAO.deleteByLiquidationId(conn, liquidationId);
            for (int i = 0; i < generatorIds.length; i++) {
                LiquidationDetail d = new LiquidationDetail();
                d.setLiquidationId(liquidationId);
                d.setGeneratorId(Integer.parseInt(generatorIds[i]));
                d.setSerialNumber(serialNumbers[i]);
                BigDecimal poPrice = purchaseOrderDAO.findApprovedUnitPriceByGenerator(d.getGeneratorId());
                d.setOriginalPrice(poPrice != null ? poPrice : BigDecimal.ZERO);
                d.setLiquidationPrice(parsedPrices[i]);
                if (detailDAO.insert(conn, d) <= 0) {
                    throw new Exception("Không lưu được dòng chi tiết cho số serial " + serialNumbers[i]);
                }
            }

            conn.commit();

            List<User> reviewers = userDAO.findUsersByPermission("liquidations", "approve_ceo");
            for (User rv : reviewers) {
                NotificationUtil.send(
                        rv.getId(),
                        "Đơn thanh lý " + l.getLiquidationCode() + " — đã sửa lại, chờ CEO duyệt",
                        user.getName() + " đã sửa lại đơn thanh lý " + l.getLiquidationCode() + " và gửi lại CEO duyệt.",
                        request.getContextPath() + "/liquidations?action=detail&id=" + liquidationId,
                        "liquidation",
                        liquidationId
                );
            }
            ActivityLog log = new ActivityLog();
            log.setUserId(user.getId());
            log.setEntityType("liquidation");
            log.setAction("EDIT_SUBMIT");
            log.setEntityId(liquidationId);
            log.setEntityName(l.getLiquidationCode());
            log.setDetails("Nhân viên đã cập nhật lại thông tin đơn thanh lý theo yêu cầu");
            activityLogDAO.insert(log);

            request.getSession().setAttribute("toastMessage", "Cập nhật đơn thanh lý thành công");
            request.getSession().setAttribute("toastType", "success");
            response.sendRedirect(request.getContextPath() + "/liquidations?action=detail&id=" + liquidationId);
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception ignored) {
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}