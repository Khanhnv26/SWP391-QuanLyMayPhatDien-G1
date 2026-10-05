package com.quanlymayphatdien.g1.controller.warehouse.receipt;

import com.quanlymayphatdien.g1.dal.CategoryDAO;
import com.quanlymayphatdien.g1.dal.GeneratorDAO;
import com.quanlymayphatdien.g1.dal.InventoryDAO;
import com.quanlymayphatdien.g1.dal.ActivityLogDAO;
import com.quanlymayphatdien.g1.dal.PurchaseOrderDAO;
import com.quanlymayphatdien.g1.dal.ReceiptDAO;
import com.quanlymayphatdien.g1.dal.ReceiptDetailDAO;
import com.quanlymayphatdien.g1.dal.TransferDetailDAO;
import com.quanlymayphatdien.g1.dal.WarehouseDAO;
import com.quanlymayphatdien.g1.dal.UserDAO;
import com.quanlymayphatdien.g1.entity.ActivityLog;
import com.quanlymayphatdien.g1.entity.Category;
import com.quanlymayphatdien.g1.entity.Generator;
import com.quanlymayphatdien.g1.entity.Inventory;
import com.quanlymayphatdien.g1.entity.PurchaseOrder;
import com.quanlymayphatdien.g1.entity.PurchaseOrderDetail;
import com.quanlymayphatdien.g1.entity.Receipt;
import com.quanlymayphatdien.g1.entity.ReceiptDetail;
import com.quanlymayphatdien.g1.entity.Transfer;
import com.quanlymayphatdien.g1.entity.TransferDetail;
import com.quanlymayphatdien.g1.entity.User;
import com.quanlymayphatdien.g1.utils.GlobalUtils;
import com.quanlymayphatdien.g1.utils.ReceiptExcelSupport;
import com.quanlymayphatdien.g1.utils.WarehouseAccessUtil;
import com.google.gson.Gson;
import com.quanlymayphatdien.g1.dal.TransferDAO;
import com.quanlymayphatdien.g1.entity.Warehouse;
import com.quanlymayphatdien.g1.utils.NotificationUtil;
import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import java.io.InputStream;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@WebServlet(name = "ImportReceiptController", urlPatterns = {"/import-receipt"})
@MultipartConfig(maxFileSize = 10 * 1024 * 1024)
public class ImportReceiptController extends HttpServlet {

    private static final String TYPE = "IMPORT";
    private final ReceiptDAO receiptDAO = new ReceiptDAO();
    private final ReceiptDetailDAO detailDAO = new ReceiptDetailDAO();
    private final WarehouseDAO warehouseDAO = new WarehouseDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAO();
    private final GeneratorDAO genDAO = new GeneratorDAO();
    private final UserDAO userDAO = new UserDAO();

    private static final int MAX_SERIAL_LENGTH = 100;
    private static final int MAX_NOTE_LENGTH = 500;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!isLoggedIn(request, response)) {
            return;
        }
        String action = request.getParameter("action");
        if (action == null || action.isEmpty()) {
            action = "list";
        }
        try {
            switch (action) {
                case "list":
                    viewList(request, response);
                    break;
                case "create":
                    showCreateForm(request, response);
                    break;
                case "detail":
                    viewDetail(request, response);
                    break;
                case "loadGenerators":
                    loadGeneratorsJson(request, response);
                    break;
                case "selectPurchase":
                    selectPurchase(request, response);
                    break;
                case "selectTransfer":
                    selectTransfer(request, response);
                    break;
                case "template":
                    downloadTemplate(request, response);
                    break;
                case "getPurchaseDetail":
                    getPurchaseDetailJson(request, response);
                    break;
                case "getTransferDetail":
                    getTransferDetailJson(request, response);
                    break;
                default:
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!isLoggedIn(request, response)) {
            return;
        }
        String action = request.getParameter("action");
        try {
            switch (action) {
                case "save":
                    saveReceipt(request, response);
                    break;
                case "importPreview":
                    importPreview(request, response);
                    break;
                default:
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (!response.isCommitted() && isAjaxRequest(request)) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                String msg = "Lỗi hệ thống: " + e.getMessage().replace("\"", "'");
                response.getWriter().write("{\"success\":false,\"message\":\"" + msg + "\"}");
            }
        }
    }

    private boolean isLoggedIn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loggedUser") == null) {
            response.sendRedirect(request.getContextPath() + "/authen?action=login");
            return false;
        }
        return true;
    }

    private void viewList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User loggedUser = (User) session.getAttribute("loggedUser");
        Set<String> perms = (Set<String>) session.getAttribute("userPermissions");
        Integer createdByFilter = null;
        if (loggedUser != null && (perms == null || !perms.contains("receipts.approve"))) {
            createdByFilter = loggedUser.getId();
        }

        int scopedWarehouseId = WarehouseAccessUtil.getScopedWarehouseId(session);
        String statusFilter = request.getParameter("status");
        String whFilter = request.getParameter("warehouse");
        if (scopedWarehouseId > 0) {
            if (whFilter != null && !whFilter.isEmpty()) {
                try {
                    if (Integer.parseInt(whFilter) != scopedWarehouseId) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN);
                        return;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            whFilter = String.valueOf(scopedWarehouseId);
        }
        String search = request.getParameter("search");
        int page = parsePage(request.getParameter("page"));
        int pageSize = 10;

        int totalItems = receiptDAO.countWithFilters(TYPE, statusFilter, whFilter, search, createdByFilter, null, loggedUser.getId());
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / pageSize));
        if (page > totalPages) {
            page = totalPages;
        }

        List<Receipt> receiptList = receiptDAO.findWithFilters(
                TYPE, statusFilter, whFilter, search, createdByFilter, null, page, pageSize, loggedUser.getId());
        int fromIndex = totalItems == 0 ? 0 : (page - 1) * pageSize + 1;
        int toIndex = Math.min(page * pageSize, totalItems);

        request.setAttribute("receiptList", receiptList);
        if (scopedWarehouseId > 0) {
            Warehouse scoped = warehouseDAO.findById(scopedWarehouseId);
            request.setAttribute("warehouses", scoped != null ? Collections.singletonList(scoped) : Collections.emptyList());
            request.setAttribute("scopedWarehouseId", scopedWarehouseId);
            if (scoped != null) {
                request.setAttribute("scopedWarehouseName", scoped.getName());
            }
        } else {
            request.setAttribute("warehouses", warehouseDAO.findAll());
        }
        request.setAttribute("statusFilter", statusFilter);
        request.setAttribute("whFilter", whFilter);
        request.setAttribute("search", search);
        request.setAttribute("currentPage", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalItems", totalItems);
        request.setAttribute("fromIndex", fromIndex);
        request.setAttribute("toIndex", toIndex);
        request.setAttribute("canApproveReceipt", perms != null && perms.contains("receipts.approve"));
        request.setAttribute("canRejectReceipt", perms != null && perms.contains("receipts.reject"));
        request.getRequestDispatcher("/view/receipt/import/import-list.jsp").forward(request, response);
    }

    private void showCreateForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        int scopedWarehouseId = WarehouseAccessUtil.getScopedWarehouseId(session);

        if (scopedWarehouseId > 0) {
            Warehouse scoped = warehouseDAO.findById(scopedWarehouseId);
            request.setAttribute("warehouses", scoped != null ? Collections.singletonList(scoped) : Collections.emptyList());
            request.setAttribute("scopedWarehouseId", scopedWarehouseId);
            if (scoped != null) {
                request.setAttribute("scopedWarehouseName", scoped.getName());
            }
        } else {
            request.setAttribute("warehouses", warehouseDAO.findAll());
        }

        setGeneratorsAttributes(request, genDAO.findAllActive());

        request.setAttribute("receiptReasons", new CategoryDAO().findByType("receipt_reason"));
        request.setAttribute("activePage", "import-create");

        String poIdStr = request.getParameter("poId");
        if (poIdStr != null && !poIdStr.isEmpty()) {
            int poId = parseId(poIdStr);
            PurchaseOrder po = new PurchaseOrderDAO().findById(poId);
            if (po != null && "APPROVED".equalsIgnoreCase(po.getStatus())) {
                if (scopedWarehouseId > 0 && po.getWarehouseId() != scopedWarehouseId) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                applyPoPrefillToRequest(request, po, "Tạo từ phiếu purchase " + po.getPoCode());
            } else if (po != null && scopedWarehouseId > 0 && po.getWarehouseId() != scopedWarehouseId) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }

        String exportReceiptIdStr = request.getParameter("exportReceiptId");
        if (exportReceiptIdStr != null && !exportReceiptIdStr.isEmpty()) {
            int exportReceiptId = parseId(exportReceiptIdStr);
            TransferDAO tDAO = new TransferDAO();
            Transfer transfer = tDAO.findByExportReceiptId(exportReceiptId);
            if (transfer != null
                    && GlobalUtils.TRANSFER_STATUS_EXPORTED.equals(transfer.getStatus())
                    && transfer.getImportReceiptId() == null) {
                if (scopedWarehouseId > 0 && scopedWarehouseId != transfer.getDestWarehouseId()) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                Receipt exportReceipt = receiptDAO.findById(exportReceiptId);
                applyExportReceiptPrefillToRequest(request, transfer, exportReceipt);
            }
        }

        request.getRequestDispatcher("/view/receipt/import/import-create.jsp").forward(request, response);
    }

    private void applyExportReceiptPrefillToRequest(HttpServletRequest request,
            Transfer transfer,
            Receipt exportReceipt) {
        if (transfer == null || exportReceipt == null) {
            return;
        }
        request.setAttribute("transfer", transfer);
        request.setAttribute("transferCode", transfer.getTransferCode());
        request.setAttribute("exportReceipt", exportReceipt);
        request.setAttribute("exportReceiptCode", exportReceipt.getReceiptCode());
        request.setAttribute("exportReceiptId", exportReceipt.getReceiptId());
        request.setAttribute("fromExportReceipt", Boolean.TRUE);
        request.setAttribute("preselectDestWarehouseId", transfer.getDestWarehouseId());

        Receipt prefill = new Receipt();
        prefill.setWarehouseId(transfer.getDestWarehouseId());
        prefill.setReceiptType(TYPE);
        prefill.setLinkedTransferId(transfer.getTransferId());
        prefill.setRelatedExportReceiptId(exportReceipt.getReceiptId());
        prefill.setNote("Nhập kho theo phiếu luân chuyển " + transfer.getTransferCode()
                + " | Phiếu xuất " + exportReceipt.getReceiptCode());
        List<ReceiptDetail> prefillDetails = new ArrayList<>();
        List<Map<String, Object>> transferRowList = new ArrayList<>();
        if (exportReceipt.getDetails() != null) {
            for (ReceiptDetail d : exportReceipt.getDetails()) {
                ReceiptDetail rd = new ReceiptDetail();
                rd.setReceiptId(0);
                rd.setInventoryId(d.getInventoryId());
                rd.setGeneratorId(d.getGeneratorId());
                rd.setSerialNumber(d.getSerialNumber());
                rd.setNote(d.getNote());
                prefillDetails.add(rd);

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("generatorId", d.getGeneratorId());
                row.put("inventoryId", d.getInventoryId());
                row.put("serialNumber", d.getSerialNumber());
                row.put("note", d.getNote() != null ? d.getNote() : "");
                transferRowList.add(row);
            }
        }
        prefill.setDetails(prefillDetails);
        request.setAttribute("receipt", prefill);
        request.setAttribute("transferRowList", transferRowList);
        request.setAttribute("expectedRows", transferRowList.size());

        Gson gson = new Gson();
        request.setAttribute("prefillDetailsJson", gson.toJson(prefillDetails));
        request.setAttribute("transferRowListJson", gson.toJson(transferRowList));

        Warehouse dest = warehouseDAO.findById(transfer.getDestWarehouseId());
        if (dest != null) {
            List<Warehouse> whList = new ArrayList<>();
            whList.add(dest);
            request.setAttribute("warehouses", whList);
        }
    }

    private void selectPurchase(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String search = request.getParameter("search");
        String fromDate = request.getParameter("fromDate");
        String toDate = request.getParameter("toDate");
        int page = parsePage(request.getParameter("page"));
        int pageSize = 10;
        HttpSession session = request.getSession(false);
        int scopedWarehouseId = WarehouseAccessUtil.getScopedWarehouseId(session);
        Integer filterWarehouseId = scopedWarehouseId > 0 ? scopedWarehouseId : null;

        PurchaseOrderDAO dao = new PurchaseOrderDAO();
        int totalItems = dao.countApprovedAvailableFiltered(search, fromDate, toDate, filterWarehouseId);
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / pageSize));
        if (page > totalPages) {
            page = totalPages;
        }
        List<PurchaseOrder> approvedPOs = dao.findApprovedAvailableFiltered(search, fromDate, toDate, filterWarehouseId, page, pageSize);
        int fromIndex = totalItems == 0 ? 0 : (page - 1) * pageSize + 1;
        int toIndex = Math.min(page * pageSize, totalItems);

        request.setAttribute("approvedPOs", approvedPOs);
        request.setAttribute("search", search);
        request.setAttribute("fromDate", fromDate);
        request.setAttribute("toDate", toDate);
        if (scopedWarehouseId > 0) {
            List<Warehouse> whList = new ArrayList<>();
            Warehouse scoped = warehouseDAO.findById(scopedWarehouseId);
            if (scoped != null) {
                whList.add(scoped);
            }
            request.setAttribute("warehouses", whList);
            request.setAttribute("scopedWarehouseId", scopedWarehouseId);
            request.setAttribute("scopedWarehouseName", scoped != null ? scoped.getName() : null);
        } else {
            request.setAttribute("warehouses", warehouseDAO.findAll());
        }
        request.setAttribute("currentPage", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalItems", totalItems);
        request.setAttribute("fromIndex", fromIndex);
        request.setAttribute("toIndex", toIndex);
        request.setAttribute("activePage", "import-select-purchase");
        request.getRequestDispatcher("/view/receipt/import/import-select-purchase.jsp").forward(request, response);
    }

    private void selectTransfer(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User loggedUser = (User) session.getAttribute("loggedUser");

        Integer scopedWarehouseId = (Integer) session.getAttribute("scopedWarehouseId");
        if (scopedWarehouseId == null) {
            scopedWarehouseId = 0;
        }

        TransferDAO tDAO = new TransferDAO();
        List<Transfer> transfers
                = tDAO.findReadyForImport(scopedWarehouseId, loggedUser.getId());

        request.setAttribute("transfers", transfers);
        request.setAttribute("totalItems", transfers.size());
        request.setAttribute("activePage", "import-select-transfer");
        request.getRequestDispatcher("/view/receipt/import/import-select-transfer.jsp").forward(request, response);
    }

    private void getPurchaseDetailJson(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        int poId = parseId(request.getParameter("id"));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        if (poId <= 0) {
            response.getWriter().write("[]");
            return;
        }
        PurchaseOrderDAO poDAO = new PurchaseOrderDAO();
        List<PurchaseOrderDetail> details = poDAO.findDetails(poId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PurchaseOrderDetail d : details) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("generatorCode", d.getGeneratorCode() != null ? d.getGeneratorCode() : "");
            item.put("generatorName", d.getGeneratorName() != null ? d.getGeneratorName() : "");
            item.put("brandName", d.getBrandName() != null ? d.getBrandName() : "");
            item.put("proposedQuantity", d.getProposedQuantity());
            item.put("finalQuantity", d.getFinalQuantity());
            item.put("unitPrice", d.getUnitPrice() != null ? d.getUnitPrice() : 0);
            item.put("note", d.getNote() != null ? d.getNote() : "");
            out.add(item);
        }
        new Gson().toJson(out, response.getWriter());
    }

    private void getTransferDetailJson(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        int transferId = parseId(request.getParameter("id"));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        if (transferId <= 0) {
            response.getWriter().write("[]");
            return;
        }
        TransferDetailDAO tdDAO = new TransferDetailDAO();
        List<TransferDetail> details = tdDAO.findByTransferId(transferId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (TransferDetail d : details) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("generatorModel", d.getGeneratorModel() != null ? d.getGeneratorModel() : "");
            item.put("serialNumber", d.getSerialNumber() != null ? d.getSerialNumber() : "");
            item.put("quantity", d.getQuantity());
            item.put("note", d.getNote() != null ? d.getNote() : "");
            out.add(item);
        }
        new Gson().toJson(out, response.getWriter());
    }

    private void applyPoPrefillToRequest(HttpServletRequest request, PurchaseOrder po, String note) {
        if (po == null) {
            return;
        }
        request.setAttribute("purchaseOrder", po);
        request.setAttribute("fromPurchaseOrder", Boolean.TRUE);
        Receipt prefill = new Receipt();
        prefill.setPurchaseOrderId(po.getPoId());
        prefill.setWarehouseId(po.getWarehouseId());
        prefill.setReceiptType(TYPE);
        prefill.setNote(note);
        List<PurchaseOrderDetail> pods = po.getDetails();
        List<ReceiptDetail> ds = new ArrayList<>();
        List<Map<String, Object>> poRowList = new ArrayList<>();
        int expectedRows = 0;
        Set<Integer> poGenIds = new HashSet<>();
        Map<Integer, Integer> poQtyMap = new LinkedHashMap<>();
        if (pods != null) {
            for (PurchaseOrderDetail pod : pods) {
                int qty = pod.getFinalQuantity() > 0 ? pod.getFinalQuantity()
                        : (pod.getProposedQuantity() > 0 ? pod.getProposedQuantity() : 1);
                poGenIds.add(pod.getGeneratorId());
                poQtyMap.merge(pod.getGeneratorId(), qty, Integer::sum);
                for (int k = 0; k < qty; k++) {
                    ReceiptDetail rd = new ReceiptDetail();
                    rd.setGeneratorId(pod.getGeneratorId());
                    rd.setNote(pod.getNote());
                    ds.add(rd);

                    Map<String, Object> poRow = new LinkedHashMap<>();
                    poRow.put("generatorId", pod.getGeneratorId());
                    poRow.put("generatorCode", pod.getGeneratorCode() != null ? pod.getGeneratorCode() : "");
                    poRow.put("generatorName", pod.getGeneratorName() != null ? pod.getGeneratorName() : "");
                    poRow.put("brandName", pod.getBrandName() != null ? pod.getBrandName() : "");
                    poRow.put("note", pod.getNote() != null ? pod.getNote() : "");
                    poRowList.add(poRow);

                    expectedRows++;
                }
            }
        }
        prefill.setDetails(ds);
        request.setAttribute("receipt", prefill);
        request.setAttribute("expectedRows", expectedRows);
        request.setAttribute("poRowList", poRowList);
        if (request.getAttribute("availableGenerators") == null) {
            List<Generator> poGenerators = new ArrayList<>();
            Map<Integer, Generator> allGenMap = new HashMap<>();
            for (Generator g : genDAO.findAllActive()) {
                allGenMap.put(g.getId(), g);
            }
            for (Integer gid : poGenIds) {
                Generator g = allGenMap.get(gid);
                if (g != null) {
                    poGenerators.add(g);
                }
            }
            request.setAttribute("availableGenerators", poGenerators);
            request.setAttribute("poQtyMap", poQtyMap);
            setGeneratorsAttributes(request, poGenerators);
        }
    }

    private Integer parsePoIdFromRequest(HttpServletRequest request) {
        String poIdStr = request.getParameter("poId");
        if (poIdStr != null && !poIdStr.isEmpty()) {
            try {
                int id = Integer.parseInt(poIdStr.trim());
                return id > 0 ? id : null;
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private boolean isPoValid(HttpServletRequest request, int poId) {
        PurchaseOrder po = new PurchaseOrderDAO().findById(poId);
        if (po == null) {
            return false;
        }
        if (!"APPROVED".equalsIgnoreCase(po.getStatus())) {
            return false;
        }
        if (!purchaseOrderBelongsToWarehouse(request, po)) {
            return false;
        }
        request.setAttribute("purchaseOrder", po);
        applyPoPrefillToRequest(request, po, "Tạo từ phiếu purchase " + po.getPoCode());
        return true;
    }

    private boolean purchaseOrderBelongsToWarehouse(HttpServletRequest request, PurchaseOrder po) {
        String whParam = request.getParameter("warehouseId");
        if (whParam == null || whParam.isEmpty()) {
            return true;
        }
        try {
            int whId = Integer.parseInt(whParam.trim());
            return whId <= 0 || whId == po.getWarehouseId();
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private void handleImportPreviewForPo(HttpServletRequest request, HttpServletResponse response,
            PurchaseOrder po, List<Map<String, String>> rawRows,
            int warehouseId, Integer reasonId, String note)
            throws ServletException, IOException {
        List<PurchaseOrderDetail> pods = po.getDetails();
        if (pods == null || pods.isEmpty()) {
            forwardBackToCreate(request, response, "PO không có chi tiết máy nào", "danger",
                    warehouseId, reasonId, note);
            return;
        }

        int expectedCount = 0;
        for (PurchaseOrderDetail pod : pods) {
            int qty = pod.getFinalQuantity() > 0 ? pod.getFinalQuantity()
                    : (pod.getProposedQuantity() > 0 ? pod.getProposedQuantity() : 1);
            expectedCount += qty;
        }

        List<String> serialList = new ArrayList<>();
        Map<String, Integer> firstSeenRow = new LinkedHashMap<>();
        List<String> duplicateSerials = new ArrayList<>();
        List<String> blockedSerials = new ArrayList<>();
        List<Integer> emptyRows = new ArrayList<>();
        List<Integer> tooLongRows = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++) {
            Map<String, String> raw = rawRows.get(i);
            String serial = raw.getOrDefault(ReceiptExcelSupport.COL_SERIAL, "").trim();
            int rowNum = i + 2;
            if (serial.isEmpty()) {
                emptyRows.add(rowNum);
                continue;
            }
            if (serial.length() > MAX_SERIAL_LENGTH) {
                tooLongRows.add(rowNum);
                blockedSerials.add("Dòng " + rowNum + ": serial quá " + MAX_SERIAL_LENGTH + " ký tự");
                continue;
            }
            Integer firstRow = firstSeenRow.get(serial);
            if (firstRow != null) {
                duplicateSerials.add("Dòng " + firstRow + " và dòng " + rowNum
                        + ": serial \"" + serial + "\" bị trùng trong file");
                continue;
            }
            firstSeenRow.put(serial, rowNum);
            if (inventoryDAO.isSerialBlocked(serial)) {
                blockedSerials.add("Dòng " + rowNum + ": serial \"" + serial + "\" đã tồn tại trong hệ thống");
                continue;
            }
            serialList.add(serial);
        }

        boolean isAjax = isAjaxRequest(request);
        if (isAjax) {
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> body = new LinkedHashMap<>();
            if (!duplicateSerials.isEmpty()) {
                body.put("success", false);
                body.put("message", "Có serial trùng trong file: " + String.join("; ", duplicateSerials));
                body.put("duplicateSerials", duplicateSerials);
                new Gson().toJson(body, response.getWriter());
                return;
            }
            if (serialList.size() != expectedCount) {
                body.put("success", false);
                body.put("message", buildSerialCountMismatchMessage(
                        serialList.size(), expectedCount,
                        emptyRows, blockedSerials));
                body.put("validCount", serialList.size());
                body.put("expectedCount", expectedCount);
                body.put("emptyRows", emptyRows);
                body.put("blockedSerials", blockedSerials);
                new Gson().toJson(body, response.getWriter());
                return;
            }
            if (!blockedSerials.isEmpty()) {
                body.put("success", false);
                body.put("message", "Có serial không hợp lệ: " + String.join("; ", blockedSerials));
                body.put("blockedSerials", blockedSerials);
                new Gson().toJson(body, response.getWriter());
                return;
            }
            List<Integer> generatorIdsList = new ArrayList<>();
            int serialIdx = 0;
            poLoop:
            for (PurchaseOrderDetail pod : pods) {
                int qty = pod.getFinalQuantity() > 0 ? pod.getFinalQuantity()
                        : (pod.getProposedQuantity() > 0 ? pod.getProposedQuantity() : 1);
                for (int k = 0; k < qty; k++) {
                    if (serialIdx >= serialList.size()) {
                        break poLoop;
                    }
                    generatorIdsList.add(pod.getGeneratorId());
                    serialIdx++;
                }
            }
            body.put("success", true);
            body.put("serials", serialList);
            body.put("generatorIds", generatorIdsList);
            body.put("expectedCount", expectedCount);
            body.put("message", "Đã đọc " + serialList.size() + " serial");
            new Gson().toJson(body, response.getWriter());
            return;
        }

        if (!duplicateSerials.isEmpty()) {
            String msg = "Có serial trùng trong file: " + String.join("; ", duplicateSerials);
            forwardBackToCreate(request, response, msg, "danger", warehouseId, reasonId, note);
            return;
        }
        if (serialList.size() != expectedCount) {
            String msg = buildSerialCountMismatchMessage(
                    serialList.size(), expectedCount,
                    emptyRows, blockedSerials);
            forwardBackToCreate(request, response, msg, "danger", warehouseId, reasonId, note);
            return;
        }
        if (!blockedSerials.isEmpty()) {
            String msg = "Có serial không hợp lệ: " + String.join("; ", blockedSerials);
            forwardBackToCreate(request, response, msg, "danger", warehouseId, reasonId, note);
            return;
        }

        applyPoPrefillToRequest(request, po, note != null ? note : "Tạo từ phiếu purchase " + po.getPoCode());
        request.setAttribute("poSerialList", serialList);
        request.setAttribute("warehouses", warehouseDAO.findAll());
        setGeneratorsAttributes(request, genDAO.findAllActive());
        request.setAttribute("receiptReasons", new CategoryDAO().findByType("receipt_reason"));
        request.setAttribute("activePage", "import-create");
        request.setAttribute("toastType", "success");
        request.setAttribute("toastMessage", "Đã đọc " + serialList.size() + " serial từ file Excel. Kiểm tra lại rồi bấm Gửi phiếu.");
        request.getRequestDispatcher("/view/receipt/import/import-create.jsp").forward(request, response);
    }

    private boolean isAjaxRequest(HttpServletRequest request) {
        String xrw = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equals(xrw)) {
            return true;
        }
        String qs = request.getQueryString();
        if (qs != null && (qs.contains("ajax=1") || qs.contains("ajax=true"))) {
            return true;
        }
        String ajax = request.getParameter("ajax");
        return "1".equals(ajax) || "true".equalsIgnoreCase(ajax);
    }

    private void validateAgainstPurchaseOrder(Integer poId, List<ReceiptDetail> details, List<String> errors) {
        if (poId == null || details == null || details.isEmpty()) {
            return;
        }
        PurchaseOrder po = new PurchaseOrderDAO().findById(poId);
        if (po == null) {
            errors.add("Không tìm thấy phiếu purchase " + poId);
            return;
        }
        Map<Integer, Integer> expectedQty = new LinkedHashMap<>();
        for (PurchaseOrderDetail pod : po.getDetails()) {
            int qty = pod.getFinalQuantity() > 0 ? pod.getFinalQuantity()
                    : (pod.getProposedQuantity() > 0 ? pod.getProposedQuantity() : 1);
            expectedQty.put(pod.getGeneratorId(), qty);
        }
        Map<Integer, Integer> actualQty = new LinkedHashMap<>();
        for (ReceiptDetail d : details) {
            if (d.getGeneratorId() <= 0) {
                continue;
            }
            actualQty.merge(d.getGeneratorId(), 1, Integer::sum);
        }
        for (Map.Entry<Integer, Integer> e : expectedQty.entrySet()) {
            int expected = e.getValue();
            int actual = actualQty.getOrDefault(e.getKey(), 0);
            if (actual != expected) {
                String label = lookupGeneratorLabel(e.getKey());
                if (actual < expected) {
                    errors.add("Thiếu " + (expected - actual) + " số serial cho máy " + label
                            + " (PO yêu cầu " + expected + ", đang có " + actual + ")");
                } else {
                    errors.add("Thừa " + (actual - expected) + " số serial cho máy " + label
                            + " (PO yêu cầu " + expected + ", đang có " + actual + ")");
                }
            }
        }
        for (Map.Entry<Integer, Integer> a : actualQty.entrySet()) {
            if (!expectedQty.containsKey(a.getKey())) {
                String label = lookupGeneratorLabel(a.getKey());
                errors.add("Máy " + label + " không có trong PO (không được thêm)");
            }
        }
    }

    private void validateTransferImportCompleteness(HttpServletRequest request,
            int exportReceiptId, List<ReceiptDetail> details, List<String> errors) {
        if (details == null || details.isEmpty()) {
            errors.add("Phiếu nhập theo luân chuyển phải chứa đủ serial từ phiếu xuất");
            return;
        }
        for (ReceiptDetail d : details) {
            if (d.getInventoryId() <= 0 && d.getSerialNumber() != null
                    && !d.getSerialNumber().trim().isEmpty()) {
                Inventory inv = inventoryDAO.findBySerialNumber(d.getSerialNumber().trim());
                if (inv != null) {
                    d.setInventoryId(inv.getInventoryId());
                }
            }
        }
        Receipt exportReceipt = new ReceiptDAO().findById(exportReceiptId);
        if (exportReceipt == null || exportReceipt.getDetails() == null
                || exportReceipt.getDetails().isEmpty()) {
            return;
        }
        Set<Integer> expectedInvIds = new HashSet<>();
        for (ReceiptDetail ed : exportReceipt.getDetails()) {
            if (ed.getInventoryId() > 0) {
                expectedInvIds.add(ed.getInventoryId());
            }
        }
        if (expectedInvIds.isEmpty()) {
            return;
        }
        Set<Integer> actualInvIds = new HashSet<>();
        for (ReceiptDetail d : details) {
            if (d.getInventoryId() > 0) {
                actualInvIds.add(d.getInventoryId());
            }
        }
        Set<Integer> missing = new HashSet<>(expectedInvIds);
        missing.removeAll(actualInvIds);
        if (!missing.isEmpty()) {
            List<String> missingSns = new ArrayList<>();
            for (ReceiptDetail ed : exportReceipt.getDetails()) {
                if (missing.contains(ed.getInventoryId())) {
                    missingSns.add(ed.getSerialNumber() != null
                            ? ed.getSerialNumber() : "(trống)");
                }
            }
            errors.add("Phiếu nhập phải chứa đủ " + expectedInvIds.size()
                    + " serial từ phiếu xuất. Còn thiếu " + missing.size()
                    + " serial: " + String.join(", ", missingSns));
            request.setAttribute("transferImportMissingSns", missingSns);
        }
    }

    private String lookupGeneratorLabel(int generatorId) {
        Generator g = genDAO.findAllActive().stream()
                .filter(x -> x.getId() == generatorId)
                .findFirst().orElse(null);
        if (g == null) {
            return "#" + generatorId;
        }
        String brand = "";
        if (g.getCategories() != null) {
            for (Category c : g.getCategories()) {
                if ("brand".equals(c.getType())) {
                    brand = c.getName();
                    break;
                }
            }
        }
        return brand.isEmpty() ? g.getModel() : (g.getModel() + " (" + brand + ")");
    }

    private void renderCreateWithError(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String whStr = request.getParameter("warehouseId");
        String reasonStr = request.getParameter("reasonId");
        String note = request.getParameter("note");
        int warehouseId = 0;
        Integer reasonId = null;
        try {
            if (whStr != null && !whStr.isEmpty()) {
                warehouseId = Integer.parseInt(whStr);
            }
        } catch (NumberFormatException ignored) {
        }
        try {
            if (reasonStr != null && !reasonStr.isEmpty()) {
                reasonId = Integer.parseInt(reasonStr);
            }
        } catch (NumberFormatException ignored) {
        }

        request.setAttribute("warehouses", warehouseDAO.findAll());
        setGeneratorsAttributes(request, genDAO.findAllActive());
        request.setAttribute("receiptReasons", new CategoryDAO().findByType("receipt_reason"));
        request.setAttribute("activePage", "import-create");

        String[] manualGenIds = request.getParameterValues("manualGeneratorId");
        String[] manualSerials = request.getParameterValues("manualSerialNumber");
        String[] manualNotes = request.getParameterValues("manualDetailNote");
        if (manualGenIds != null) {
            List<Map<String, String>> manualRows = new ArrayList<>();
            for (int i = 0; i < manualGenIds.length; i++) {
                String gid = manualGenIds[i] != null ? manualGenIds[i] : "";
                String sn = (manualSerials != null && i < manualSerials.length) ? manualSerials[i] : "";
                String nt = (manualNotes != null && i < manualNotes.length) ? manualNotes[i] : "";
                if (gid.isEmpty() && sn.isEmpty() && nt.isEmpty()) {
                    continue;
                }
                Map<String, String> r = new LinkedHashMap<>();
                r.put("generatorId", gid);
                r.put("serialNumber", sn);
                r.put("detailNote", nt);
                manualRows.add(r);
            }
            if (!manualRows.isEmpty()) {
                request.setAttribute("preservedManualRows", manualRows);
            }
        }
        request.setAttribute("preservedWarehouseId", warehouseId);
        request.setAttribute("preservedReasonId", reasonId);
        request.setAttribute("preservedNote", note);

        Integer poId = parsePoIdFromRequest(request);
        if (poId != null) {
            PurchaseOrder po = new PurchaseOrderDAO().findById(poId);
            if (po != null) {
                applyPoPrefillToRequest(request, po, "Tạo từ phiếu purchase " + po.getPoCode());
            }
        }

        request.getRequestDispatcher("/view/receipt/import/import-create.jsp").forward(request, response);
    }

    private void errorsForPo(HttpServletRequest request, String message) {
        request.setAttribute("toastType", "danger");
        request.setAttribute("toastMessage", message);
    }

    private void viewDetail(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User loggedUser = (User) session.getAttribute("loggedUser");
        int id = parseId(request.getParameter("id"));
        if (id <= 0) {
            response.sendRedirect(request.getContextPath() + "/import-receipt");
            return;
        }

        Receipt receipt = receiptDAO.findById(id);
        if (receipt == null) {
            request.setAttribute("error", "Không tìm thấy phiếu");
            request.getRequestDispatcher("/view/receipt/import/import-detail.jsp").forward(request, response);
            return;
        }
        if (!TYPE.equals(receipt.getReceiptType())) {
            response.sendRedirect(request.getContextPath() + "/export-receipt?action=detail&id=" + id);
            return;
        }
        int scopedWarehouseId = WarehouseAccessUtil.getScopedWarehouseId(session);
        if (scopedWarehouseId > 0 && receipt.getWarehouseId() != scopedWarehouseId) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Set<String> perms = (Set<String>) session.getAttribute("userPermissions");
        boolean isManager = perms != null && perms.contains("receipts.approve");
        boolean isOwner = receipt.getCreatedBy() == loggedUser.getId();

        String tab = request.getParameter("tab");
        String currentTab = "history".equals(tab) ? "history" : "info";
        request.setAttribute("currentTab", currentTab);

        if ("history".equals(currentTab)) {
            String logSearch = request.getParameter("logSearch");
            String logAction = request.getParameter("logAction");
            String dateFrom = request.getParameter("dateFrom");
            String dateTo = request.getParameter("dateTo");
            int page = 1;
            int pageSize = 20;
            String pageStr = request.getParameter("page");
            if (pageStr != null && !pageStr.isEmpty()) {
                try {
                    page = Math.max(1, Integer.parseInt(pageStr));
                } catch (NumberFormatException ignored) {
                    page = 1;
                }
            }
            List<ActivityLog> logs = activityLogDAO.findByEntityTypeAndId2("receipt", id, logSearch, logAction,
                    dateFrom, dateTo, page, pageSize);
            int totalLogs = activityLogDAO.countByEntityTypeAndId2("receipt", id, logSearch, logAction,
                    dateFrom, dateTo);
            int totalPages = Math.max(1, (int) Math.ceil((double) totalLogs / pageSize));
            if (page > totalPages) {
                page = totalPages;
            }
            request.setAttribute("logList", logs);
            request.setAttribute("logPage", page);
            request.setAttribute("logTotalPages", totalPages);
            request.setAttribute("totalLogs", totalLogs);
            request.setAttribute("logSearch", logSearch != null ? logSearch : "");
            request.setAttribute("logAction", logAction != null ? logAction : "");
            request.setAttribute("dateFrom", dateFrom != null ? dateFrom : "");
            request.setAttribute("dateTo", dateTo != null ? dateTo : "");
        }

        List<ReceiptDetail> allDetails = receipt.getDetails();
        int totalDetails = allDetails != null ? allDetails.size() : 0;
        int detailPageSize = 10;
        int detailPage = 1;
        String detailPageStr = request.getParameter("detailPage");
        if (detailPageStr != null && !detailPageStr.isEmpty()) {
            try {
                detailPage = Math.max(1, Integer.parseInt(detailPageStr));
            } catch (NumberFormatException ignored) {
            }
        }
        int detailTotalPages = Math.max(1, (int) Math.ceil((double) totalDetails / detailPageSize));
        if (detailPage > detailTotalPages) {
            detailPage = detailTotalPages;
        }
        List<ReceiptDetail> pagedDetails = new ArrayList<>();
        if (allDetails != null && !allDetails.isEmpty()) {
            int fromIndex = (detailPage - 1) * detailPageSize;
            int toIndex = Math.min(fromIndex + detailPageSize, allDetails.size());
            pagedDetails = allDetails.subList(fromIndex, toIndex);
        }
        request.setAttribute("pagedDetails", pagedDetails);
        request.setAttribute("detailPage", detailPage);
        request.setAttribute("detailTotalPages", detailTotalPages);
        request.setAttribute("totalDetails", totalDetails);

        request.setAttribute("receipt", receipt);
        request.setAttribute("isManager", isManager);
        request.setAttribute("isOwner", isOwner);
        request.setAttribute("activePage", "import-detail");
        request.getRequestDispatcher("/view/receipt/import/import-detail.jsp").forward(request, response);
    }

    private void loadGeneratorsJson(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        int scopedWarehouseId = WarehouseAccessUtil.getScopedWarehouseId(session);
        int whId = parseId(request.getParameter("warehouseId"));
        if (scopedWarehouseId > 0) {
            if (whId > 0 && whId != scopedWarehouseId) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            whId = scopedWarehouseId;
        }
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        if (whId <= 0) {
            response.getWriter().write("[]");
            return;
        }
        List<Generator> generators = genDAO.findAllActive();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Generator g : generators) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", g.getId());
            item.put("model", g.getModel());
            String brand = "";
            if (g.getCategories() != null) {
                for (Category c : g.getCategories()) {
                    if ("brand".equals(c.getType())) {
                        brand = c.getName();
                        break;
                    }
                }
            }
            item.put("brand", brand);
            int stockQty = inventoryDAO.findInStockByWarehouseAndGenerator(whId, g.getId()).size();
            item.put("stockQty", stockQty);
            out.add(item);
        }
        new Gson().toJson(out, response.getWriter());
    }

    private void saveReceipt(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User loggedUser = (User) session.getAttribute("loggedUser");
        int scopedWarehouseId = WarehouseAccessUtil.getScopedWarehouseId(session);

        Integer poId = parsePoIdFromRequest(request);
        boolean fromPo = poId != null;
        if (fromPo && !isPoValid(request, poId)) {
            errorsForPo(request, "Phiếu mua không hợp lệ hoặc chưa được duyệt");
            renderCreateWithError(request, response);
            return;
        }

        int warehouseId = parseId(request.getParameter("warehouseId"));
        String note = request.getParameter("note");
        Integer reasonId = null;
        List<String> errors = new ArrayList<>();

        if (warehouseId <= 0) {
            errors.add("Vui lòng chọn kho");
        }
        if (scopedWarehouseId > 0 && warehouseId > 0 && warehouseId != scopedWarehouseId) {
            errors.add("Bạn chỉ được phép tạo phiếu nhập cho kho của mình");
        }
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            errors.add("Ghi chú phiếu không được vượt quá " + MAX_NOTE_LENGTH + " ký tự");
        }

        String reasonIdStr = request.getParameter("reasonId");
        if (reasonIdStr != null && !reasonIdStr.isEmpty()) {
            try {
                reasonId = Integer.parseInt(reasonIdStr);
            } catch (NumberFormatException e) {
                errors.add("Lý do không hợp lệ");
            }
        } else {
            errors.add("Vui lòng chọn lý do");
        }

        String[] genIds = request.getParameterValues("manualGeneratorId");
        String[] serials = request.getParameterValues("manualSerialNumber");
        String[] detailNotes = request.getParameterValues("manualDetailNote");
        int selectedGeneratorId = parseId(request.getParameter("selectedGeneratorId"));
        int exportReceiptId = parseId(request.getParameter("exportReceiptId"));

        boolean hasAnyManualGenId = genIds != null && java.util.Arrays.stream(genIds)
                .anyMatch(id -> id != null && !id.trim().isEmpty() && !"0".equals(id.trim()));

        if (!fromPo && exportReceiptId <= 0 && !hasAnyManualGenId && selectedGeneratorId <= 0) {
            errors.add("Vui lòng chọn mẫu máy phát điện ở mục 02 trước khi gửi phiếu");
        }

        if (!fromPo && exportReceiptId <= 0 && !hasAnyManualGenId && selectedGeneratorId > 0 && genIds != null) {
            for (int i = 0; i < genIds.length; i++) {
                if (genIds[i] == null || genIds[i].trim().isEmpty()
                        || "0".equals(genIds[i].trim())) {
                    genIds[i] = String.valueOf(selectedGeneratorId);
                }
            }
        }

        List<ReceiptDetail> details;
        if (fromPo) {
            details = parseDetailsStrict(genIds, serials, detailNotes, errors);
            if (details.isEmpty()) {
                errors.add("Phiếu nhập từ PO phải có ít nhất 1 dòng số serial hợp lệ");
            }
            validateAgainstPurchaseOrder(poId, details, errors);
        } else {
            details = parseDetailsStrict(genIds, serials, detailNotes, errors);
            if (details.isEmpty() && errors.stream().noneMatch(s -> s.startsWith("Dòng "))) {
                errors.add("Phải có ít nhất 1 dòng chi tiết hợp lệ");
            }
        }

        if (exportReceiptId > 0) {
            validateTransferImportCompleteness(request, exportReceiptId, details, errors);
        }

        if (!errors.isEmpty()) {
            request.setAttribute("toastType", "danger");
            request.setAttribute("toastMessage", buildErrorMessage("Lưu phiếu thất bại:", errors));
            request.setAttribute("errors", errors);

            request.setAttribute("preservedWarehouseId", warehouseId);
            request.setAttribute("preservedReasonId", reasonId);
            request.setAttribute("preservedNote", note);
            if (selectedGeneratorId > 0) {
                request.setAttribute("preservedSelectedGeneratorId", selectedGeneratorId);
            }

            String[] manualGenIds = request.getParameterValues("manualGeneratorId");
            String[] manualSerials = request.getParameterValues("manualSerialNumber");
            String[] manualNotes = request.getParameterValues("manualDetailNote");
            if (manualGenIds != null && manualGenIds.length > 0) {
                List<Map<String, String>> manualRows = new ArrayList<>();
                for (int i = 0; i < manualGenIds.length; i++) {
                    String gid = manualGenIds[i] != null ? manualGenIds[i] : "";
                    if ((gid == null || gid.isEmpty() || "0".equals(gid)) && selectedGeneratorId > 0) {
                        gid = String.valueOf(selectedGeneratorId);
                    }
                    String sn = (manualSerials != null && i < manualSerials.length) ? manualSerials[i] : "";
                    String nt = (manualNotes != null && i < manualNotes.length) ? manualNotes[i] : "";
                    if ((gid == null || gid.isEmpty()) && (sn == null || sn.isEmpty()) && (nt == null || nt.isEmpty())) {
                        continue;
                    }
                    Map<String, String> r = new LinkedHashMap<>();
                    r.put("generatorId", gid);
                    r.put("serialNumber", sn);
                    r.put("detailNote", nt);
                    manualRows.add(r);
                }
                if (!manualRows.isEmpty()) {
                    request.setAttribute("preservedManualRows", manualRows);
                }
            }

            Integer poIdErr = parsePoIdFromRequest(request);
            if (poIdErr != null) {
                PurchaseOrder poErr = new PurchaseOrderDAO().findById(poIdErr);
                if (poErr != null && "APPROVED".equalsIgnoreCase(poErr.getStatus())) {
                    applyPoPrefillToRequest(request, poErr, "Tạo từ phiếu purchase " + poErr.getPoCode());
                }
            }

            if (exportReceiptId > 0) {
                TransferDAO tDAO = new TransferDAO();
                Transfer transferErr = tDAO.findByExportReceiptId(exportReceiptId);
                Receipt exportReceiptErr = new ReceiptDAO().findById(exportReceiptId);
                if (transferErr != null && exportReceiptErr != null) {
                    applyExportReceiptPrefillToRequest(request, transferErr, exportReceiptErr);
                }
            }

            request.setAttribute("warehouses", warehouseDAO.findAll());
            setGeneratorsAttributes(request, genDAO.findAllActive());
            request.setAttribute("receiptReasons", new CategoryDAO().findByType("receipt_reason"));
            request.setAttribute("activePage", "import-create");

            request.getRequestDispatcher("/view/receipt/import/import-create.jsp").forward(request, response);
            return;
        }

        boolean isTransferImport = exportReceiptId > 0;
        if (!isTransferImport) {
            for (ReceiptDetail d : details) {
                if (d.getSerialNumber() != null && !d.getSerialNumber().trim().isEmpty()) {
                    if (inventoryDAO.isSerialBlocked(d.getSerialNumber())) {
                        errors.add("Serial \"" + d.getSerialNumber() + "\" đã tồn tại trong hệ thống");
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            request.setAttribute("toastType", "danger");
            request.setAttribute("toastMessage", buildErrorMessage("Lưu phiếu thất bại:", errors));
            request.setAttribute("errors", errors);
            request.setAttribute("warehouses", warehouseDAO.findAll());
            setGeneratorsAttributes(request, genDAO.findAllActive());
            request.setAttribute("receiptReasons", new CategoryDAO().findByType("receipt_reason"));
            request.getRequestDispatcher("/view/receipt/import/import-create.jsp").forward(request, response);
            return;
        }

        Transfer transferForImport = null;
        Receipt exportReceipt = null;
        if (exportReceiptId > 0) {
            TransferDAO tDAO = new TransferDAO();
            transferForImport = tDAO.findByExportReceiptId(exportReceiptId);
            if (transferForImport == null
                    || !GlobalUtils.TRANSFER_STATUS_EXPORTED.equals(transferForImport.getStatus())
                    || transferForImport.getImportReceiptId() != null) {
                HttpSession s = request.getSession();
                s.setAttribute("toastMessage", "Phiếu đề xuất không hợp lệ hoặc đã có phiếu nhập");
                s.setAttribute("toastType", "danger");
                response.sendRedirect(request.getContextPath()
                        + "/transfers?action=detail&id=" + (transferForImport != null ? transferForImport.getTransferId() : 0));
                return;
            }
            if (warehouseId != transferForImport.getDestWarehouseId()) {
                HttpSession s = request.getSession();
                s.setAttribute("toastMessage", "Kho nhập phải là kho đích của phiếu luân chuyển");
                s.setAttribute("toastType", "danger");
                response.sendRedirect(request.getContextPath()
                        + "/transfers?action=detail&id=" + transferForImport.getTransferId());
                return;
            }
            exportReceipt = receiptDAO.findById(exportReceiptId);
            if (exportReceipt == null) {
                HttpSession s = request.getSession();
                s.setAttribute("toastMessage", "Không tìm thấy phiếu xuất gốc");
                s.setAttribute("toastType", "danger");
                response.sendRedirect(request.getContextPath() + "/transfers");
                return;
            }
            isTransferImport = true;
        }

        Receipt r = new Receipt();
        r.setReceiptCode(receiptDAO.generateReceiptCode(TYPE));
        r.setReceiptType(TYPE);
        r.setWarehouseId(warehouseId);
        r.setCreatedBy(loggedUser.getId());
        r.setNote(note);
        r.setReasonId(reasonId);
        String pid = request.getParameter("poId");
        if (pid != null && !pid.isEmpty()) {
            try {
                r.setPurchaseOrderId(Integer.parseInt(pid));
            } catch (NumberFormatException ignored) {
            }
        }
        if (isTransferImport && transferForImport != null) {
            r.setLinkedTransferId(transferForImport.getTransferId());
            r.setRelatedExportReceiptId(exportReceiptId);
        }
        r.setStatus(GlobalUtils.RECEIPT_STATUS_COMPLETED);
        r.setApprovedBy(loggedUser.getId());
        r.setApprovedAt(LocalDateTime.now());

        List<ReceiptDetail> detailsToSave;
        if (isTransferImport && exportReceipt != null && exportReceipt.getDetails() != null) {
            detailsToSave = exportReceipt.getDetails();
        } else {
            detailsToSave = details;
        }
        int receiptId;
        try {
            receiptId = receiptDAO.insertImport(r, detailsToSave, loggedUser.getId());
        } catch (SQLException ex) {
            errors.add("Lỗi hệ thống khi lưu phiếu: " + ex.getMessage());
            request.setAttribute("toastType", "danger");
            request.setAttribute("toastMessage", buildErrorMessage("Lưu phiếu thất bại:", errors));
            request.setAttribute("warehouses", warehouseDAO.findAll());
            setGeneratorsAttributes(request, genDAO.findAllActive());
            request.setAttribute("receiptReasons", new CategoryDAO().findByType("receipt_reason"));
            request.getRequestDispatcher("/view/receipt/import/import-create.jsp").forward(request, response);
            return;
        }

        ActivityLog log = new ActivityLog();
        log.setUserId(loggedUser.getId());
        log.setEntityType("receipt");
        log.setAction("CREATE");
        log.setEntityId(receiptId);
        log.setEntityName(r.getReceiptCode());
        log.setDetails(isTransferImport
                ? "Tạo phiếu nhập theo phiếu luân chuyển " + transferForImport.getTransferCode()
                : "Tạo phiếu nhập kho và cập nhật tồn kho");
        activityLogDAO.insert(log);

        if (isTransferImport && transferForImport != null) {
            TransferDAO tDAO = new TransferDAO();
            if (tDAO.markImportReceiptCreated(transferForImport.getTransferId(), receiptId, loggedUser.getId())) {
                ActivityLog transferLog = new ActivityLog();
                transferLog.setUserId(loggedUser.getId());
                transferLog.setEntityType("transfer");
                transferLog.setAction("IMPORT_CREATED");
                transferLog.setEntityId(transferForImport.getTransferId());
                transferLog.setEntityName(transferForImport.getTransferCode());
                transferLog.setDetails("Phiếu nhập " + r.getReceiptCode()
                        + " đã hoàn tất (EXPORTED -> COMPLETED)");
                activityLogDAO.insert(transferLog);

                notifySourceWarehouseStaff(transferForImport, r, loggedUser, request.getContextPath());
            }
        }

        session.setAttribute("toastMessage", isTransferImport
                ? "Tạo phiếu nhập thành công. Phiếu luân chuyển đã hoàn tất."
                : "Thêm phiếu thành công");
        session.setAttribute("toastType", "success");
        if (isTransferImport) {
            response.sendRedirect(request.getContextPath()
                    + "/transfers?action=detail&id=" + transferForImport.getTransferId());
        } else {
            response.sendRedirect(request.getContextPath() + "/import-receipt?action=list");
        }
    }

    private void downloadTemplate(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String poIdStr = request.getParameter("poId");
        List<PurchaseOrderDetail> poDetails = null;
        if (poIdStr != null && !poIdStr.isEmpty()) {
            int poId = parseId(poIdStr);
            if (poId > 0) {
                PurchaseOrder po = new PurchaseOrderDAO().findById(poId);
                if (po == null) {
                    redirectWithToast(request, response,
                            "Phiếu mua #" + poId + " không tồn tại", "danger",
                            "/import-receipt?action=create&poId=" + poId);
                    return;
                }
                if (!"APPROVED".equalsIgnoreCase(po.getStatus())) {
                    redirectWithToast(request, response,
                            "Phiếu mua " + po.getPoCode() + " chưa được duyệt (hiện tại: " + po.getStatus() + ")",
                            "danger",
                            "/import-receipt?action=create&poId=" + poId);
                    return;
                }
                poDetails = po.getDetails();
                if (poDetails == null || poDetails.isEmpty()) {
                    redirectWithToast(request, response,
                            "Phiếu mua " + po.getPoCode() + " không có chi tiết máy nào để nhập",
                            "danger",
                            "/import-receipt?action=create&poId=" + poId);
                    return;
                }
            }
        }

        String exportReceiptIdStr = request.getParameter("exportReceiptId");
        if (exportReceiptIdStr != null && !exportReceiptIdStr.isEmpty() && poDetails == null) {
            int exportReceiptId = parseId(exportReceiptIdStr);
            if (exportReceiptId > 0) {
                GeneratorDAO genDAO = new GeneratorDAO();
                List<Generator> allGens = genDAO.findAllActive();
                ReceiptDAO receiptDAO = new ReceiptDAO();
                Receipt exportReceipt = receiptDAO.findById(exportReceiptId);
                if (exportReceipt != null && exportReceipt.getDetails() != null
                        && !exportReceipt.getDetails().isEmpty()) {
                    XSSFWorkbook workbook = ReceiptExcelSupport.createTemplateWorkbook(
                            exportReceipt.getDetails(), allGens);
                    String fileName = "mau-phieu-nhap-" + new SimpleDateFormat("dd/MM/yyyy").format(new Date()) + ".xlsx";
                    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                    workbook.write(response.getOutputStream());
                    workbook.close();
                    return;
                }
            }
        }

        XSSFWorkbook workbook = ReceiptExcelSupport.createTemplateWorkbook(poDetails);
        String fileName = "mau-phieu-nhap-" + new SimpleDateFormat("dd/MM/yyyy").format(new Date()) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    private void redirectWithToast(HttpServletRequest request, HttpServletResponse response,
            String message, String type, String redirectPath) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute("toastMessage", message);
            session.setAttribute("toastType", type);
        }
        response.sendRedirect(request.getContextPath() + redirectPath);
    }

    private void importPreview(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User loggedUser = (User) session.getAttribute("loggedUser");
        if (loggedUser == null) {
            response.sendRedirect(request.getContextPath() + "/authen?action=login");
            return;
        }

        int warehouseId = parseId(request.getParameter("warehouseId"));
        Integer reasonId = null;
        String reasonIdStr = request.getParameter("reasonId");
        if (reasonIdStr != null && !reasonIdStr.isEmpty()) {
            try {
                reasonId = Integer.parseInt(reasonIdStr);
            } catch (NumberFormatException ignored) {
            }
        }
        String note = request.getParameter("note");

        Part filePart = null;
        try {
            filePart = request.getPart("excelFile");
        } catch (Exception e) {
            forwardBackToCreate(request, response, "Không đọc được file upload: " + e.getMessage(), "danger",
                    warehouseId, reasonId, note);
            return;
        }

        if (filePart == null || filePart.getSize() == 0) {
            forwardBackToCreate(request, response, "Vui lòng chọn file Excel để nhập", "danger",
                    warehouseId, reasonId, note);
            return;
        }

        List<Map<String, String>> rawRows;
        try (InputStream is = filePart.getInputStream()) {
            rawRows = ReceiptExcelSupport.parseFromExcel(is);
        } catch (Exception e) {
            forwardBackToCreate(request, response, "File Excel không hợp lệ: " + e.getMessage(), "danger",
                    warehouseId, reasonId, note);
            return;
        }

        Integer poId = parsePoIdFromRequest(request);
        if (poId != null) {
            PurchaseOrder po = new PurchaseOrderDAO().findById(poId);
            if (po == null || !"APPROVED".equalsIgnoreCase(po.getStatus())) {
                forwardBackToCreate(request, response, "Phiếu mua không hợp lệ hoặc chưa được duyệt", "danger",
                        warehouseId, reasonId, note);
                return;
            }
            if (po.getWarehouseId() != warehouseId) {
                forwardBackToCreate(request, response,
                        "Kho trong file Excel khác với kho của PO (PO yêu cầu kho "
                        + po.getWarehouseId() + ")", "danger",
                        warehouseId, reasonId, note);
                return;
            }
            handleImportPreviewForPo(request, response, po, rawRows, warehouseId, reasonId, note);
            return;
        }

        if (rawRows.isEmpty()) {
            forwardBackToCreate(request, response,
                    "File Excel trống hoặc không đúng định dạng. Hãy dùng file mẫu (Tải mẫu Excel) với 3 cột: "
                    + ReceiptExcelSupport.COL_MODEL + " | " + ReceiptExcelSupport.COL_SERIAL + " | " + ReceiptExcelSupport.COL_NOTE,
                    "danger", warehouseId, reasonId, note);
            return;
        }

        Map<String, String> firstRow = rawRows.get(0);
        boolean hasModelKey = firstRow.containsKey(ReceiptExcelSupport.COL_MODEL);
        boolean hasSerialKey = firstRow.containsKey(ReceiptExcelSupport.COL_SERIAL);
        if (!hasModelKey || !hasSerialKey) {
            forwardBackToCreate(request, response,
                    "File Excel thiếu cột bắt buộc. Cần có 2 cột: \""
                    + ReceiptExcelSupport.COL_MODEL + "\" và \"" + ReceiptExcelSupport.COL_SERIAL + "\". "
                    + "Có thể dùng tên thay thế: Model/Generator cho mã máy, Serial/SN cho số serial. "
                    + "Hãy tải file mẫu và copy dữ liệu vào.",
                    "danger", warehouseId, reasonId, note);
            return;
        }

        List<Generator> allGenerators = genDAO.findAllActive();
        Map<String, Generator> modelIndex = new HashMap<>();
        for (Generator g : allGenerators) {
            if (g.getModel() != null) {
                modelIndex.put(g.getModel().trim().toLowerCase(), g);
            }
        }

        String exportReceiptIdStr = request.getParameter("exportReceiptId");
        boolean isTransferImport = exportReceiptIdStr != null && !exportReceiptIdStr.isEmpty();

        List<Map<String, Object>> validRows = new ArrayList<>();
        List<Map<String, Object>> invalidRows = new ArrayList<>();
        Map<String, Integer> firstSeenRow = new LinkedHashMap<>();

        for (int i = 0; i < rawRows.size(); i++) {
            Map<String, String> raw = rawRows.get(i);
            int rowNum = i + 2;

            String model = raw.getOrDefault(ReceiptExcelSupport.COL_MODEL, "").trim();
            String serial = raw.getOrDefault(ReceiptExcelSupport.COL_SERIAL, "").trim();
            String detailNote = raw.getOrDefault(ReceiptExcelSupport.COL_NOTE, "").trim();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rowNum", rowNum);
            row.put("model", model);
            row.put("serial", serial);
            row.put("note", detailNote);

            List<String> errors = new ArrayList<>();

            if (model.isEmpty()) {
                errors.add("Thiếu mã máy");
            }
            if (serial.isEmpty()) {
                errors.add("Thiếu số serial");
            } else if (serial.length() > MAX_SERIAL_LENGTH) {
                errors.add("Số serial vượt quá " + MAX_SERIAL_LENGTH + " ký tự");
            }

            Generator resolved = null;
            if (!model.isEmpty()) {
                resolved = modelIndex.get(model.toLowerCase());
                if (resolved == null) {
                    try {
                        int gid = Integer.parseInt(model);
                        for (Generator g : allGenerators) {
                            if (g.getId() == gid) {
                                resolved = g;
                                break;
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (resolved == null) {
                    errors.add("Không tìm thấy máy \"" + model + "\"");
                } else if (!"active".equalsIgnoreCase(resolved.getStatus())) {
                    errors.add("Máy \"" + model + "\" không hoạt động");
                }
            }

            if (detailNote.length() > MAX_NOTE_LENGTH) {
                errors.add("Ghi chú vượt quá " + MAX_NOTE_LENGTH + " ký tự");
            }

            if (!serial.isEmpty()) {
                Integer firstSeenIndex = firstSeenRow.get(serial);
                if (firstSeenIndex != null) {
                    errors.add("Dòng " + firstSeenIndex + " và dòng " + rowNum
                            + ": số serial \"" + serial + "\" bị trùng trong file");
                } else {
                    firstSeenRow.put(serial, rowNum);
                }
            }

            if (!serial.isEmpty()) {
                Inventory inv = inventoryDAO.findBySerialNumber(serial);
                if (isTransferImport) {
                    if (inv == null) {
                        errors.add("Số serial \"" + serial + "\" không tồn tại trong hệ thống, không thể nhập từ phiếu luân chuyển");
                    } else if (!"IN_TRANSIT".equals(inv.getStatus()) && !"SOLD".equals(inv.getStatus())) {
                        String statusName = "IN_STOCK".equals(inv.getStatus()) ? "tồn kho"
                                : "IN_TRANSIT".equals(inv.getStatus()) ? "đang luân chuyển"
                                : "SOLD".equals(inv.getStatus()) ? "đã bán"
                                : inv.getStatus() != null ? inv.getStatus().toLowerCase() : "không xác định";
                        errors.add("Số serial \"" + serial + "\" đang ở trạng thái " + statusName + ", không thể nhập từ phiếu luân chuyển");
                    }
                } else {
                    if (inv != null && !"SOLD".equals(inv.getStatus())) {
                        errors.add("Số serial \"" + serial + "\" đã tồn tại trong hệ thống");
                    }
                }
            }

            if (resolved != null) {
                row.put("generatorId", resolved.getId());
                row.put("generatorModel", resolved.getModel());
                String brand = "";
                if (resolved.getCategories() != null) {
                    for (Category c : resolved.getCategories()) {
                        if ("brand".equals(c.getType())) {
                            brand = c.getName();
                            break;
                        }
                    }
                }
                row.put("brand", brand);
            }

            if (errors.isEmpty()) {
                validRows.add(row);
            } else {
                row.put("_errors", String.join("; ", errors));
                invalidRows.add(row);
            }
        }

        if (!isAjaxRequest(request)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", "API importPreview chỉ hỗ trợ AJAX");
            new Gson().toJson(body, response.getWriter());
            return;
        }

        if (validRows.isEmpty() && !invalidRows.isEmpty()) {
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            List<String> errorMessages = new ArrayList<>();
            for (Map<String, Object> inv : invalidRows) {
                Object e = inv.get("_errors");
                if (e != null) {
                    errorMessages.add("Dòng " + inv.get("rowNum") + ": " + e);
                }
            }
            body.put("message", "File Excel không có dòng hợp lệ nào. "
                    + String.join("; ", errorMessages));
            new Gson().toJson(body, response.getWriter());
            return;
        }

        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "Đã đọc " + validRows.size() + " dòng từ Excel"
                + (invalidRows.isEmpty() ? "" : " (bỏ qua " + invalidRows.size() + " dòng lỗi)"));
        List<String> serials = new ArrayList<>();
        List<Integer> generatorIds = new ArrayList<>();
        List<String> models = new ArrayList<>();
        for (Map<String, Object> v : validRows) {
            serials.add((String) v.get("serial"));
            generatorIds.add((Integer) v.get("generatorId"));
            Object m = v.get("model");
            models.add(m == null ? "" : m.toString());
        }
        body.put("serials", serials);
        body.put("generatorIds", generatorIds);
        body.put("models", models);
        new Gson().toJson(body, response.getWriter());
    }

    private void forwardBackToCreate(HttpServletRequest request, HttpServletResponse response,
            String toastMessage, String toastType,
            int warehouseId, Integer reasonId, String note)
            throws ServletException, IOException {
        if (isAjaxRequest(request)) {
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", toastMessage);
            new Gson().toJson(body, response.getWriter());
            return;
        }
        request.setAttribute("toastType", toastType);
        request.setAttribute("toastMessage", toastMessage);
        preserveFormStateForInlinePreview(request, warehouseId, reasonId, note);
        request.getRequestDispatcher("/view/receipt/import/import-create.jsp").forward(request, response);
    }

    private void preserveFormStateForInlinePreview(HttpServletRequest request,
            int warehouseId, Integer reasonId, String note) {
        request.setAttribute("warehouses", warehouseDAO.findAll());
        setGeneratorsAttributes(request, genDAO.findAllActive());
        request.setAttribute("receiptReasons", new CategoryDAO().findByType("receipt_reason"));
        request.setAttribute("activePage", "import-create");

        request.setAttribute("preservedWarehouseId", warehouseId);
        request.setAttribute("preservedReasonId", reasonId);
        request.setAttribute("preservedNote", note);

        String[] manualGenIds = request.getParameterValues("manualGeneratorId");
        String[] manualSerials = request.getParameterValues("manualSerialNumber");
        String[] manualNotes = request.getParameterValues("manualDetailNote");
        if (manualGenIds != null && manualGenIds.length > 0) {
            List<Map<String, String>> manualRows = new ArrayList<>();
            for (int i = 0; i < manualGenIds.length; i++) {
                String gid = manualGenIds[i] != null ? manualGenIds[i] : "";
                String sn = (manualSerials != null && i < manualSerials.length) ? manualSerials[i] : "";
                String nt = (manualNotes != null && i < manualNotes.length) ? manualNotes[i] : "";
                if (gid.isEmpty() && sn.isEmpty() && nt.isEmpty()) {
                    continue;
                }
                Map<String, String> r = new LinkedHashMap<>();
                r.put("generatorId", gid);
                r.put("serialNumber", sn);
                r.put("detailNote", nt);
                manualRows.add(r);
            }
            if (!manualRows.isEmpty()) {
                request.setAttribute("preservedManualRows", manualRows);
            }
        }

        String poIdStr = request.getParameter("poId");
        if (poIdStr != null && !poIdStr.isEmpty()) {
            request.setAttribute("preservedPoId", poIdStr);
        }
    }

    private int parseId(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int parsePage(String s) {
        if (s == null || s.isEmpty()) {
            return 1;
        }
        try {
            int p = Integer.parseInt(s);
            return p < 1 ? 1 : p;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private Map<Integer, String> buildBrandMap(List<Generator> generators) {
        Map<Integer, String> brandMap = new LinkedHashMap<>();
        for (Generator g : generators) {
            String brand = "";
            if (g.getCategories() != null) {
                for (Category c : g.getCategories()) {
                    if ("brand".equals(c.getType())) {
                        brand = c.getName();
                        break;
                    }
                }
            }
            brandMap.put(g.getId(), brand);
        }
        return brandMap;
    }

    private void setGeneratorsAttributes(HttpServletRequest request, List<Generator> generators) {
        request.setAttribute("generators", generators);
        request.setAttribute("brandMap", buildBrandMap(generators));
        Gson gson = new Gson();
        List<Map<String, Object>> genJsonList = new ArrayList<>();
        for (Generator g : generators) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", g.getId());
            item.put("model", g.getModel());
            String brand = "";
            if (g.getCategories() != null) {
                for (Category c : g.getCategories()) {
                    if ("brand".equals(c.getType())) {
                        brand = c.getName();
                        break;
                    }
                }
            }
            item.put("brand", brand);
            item.put("stockQty", null);
            genJsonList.add(item);
        }
        request.setAttribute("generatorsJson", gson.toJson(genJsonList));
    }

    private String buildErrorMessage(String prefix, List<String> errors) {
        StringBuilder msg = new StringBuilder(prefix);
        for (String e : errors) {
            msg.append(" ").append(e.replace("Dòng ", "dòng "));
        }
        return msg.toString();
    }

    private String buildSerialCountMismatchMessage(int validCount, int expectedCount,
            List<Integer> emptyRows, List<String> blockedSerials) {
        StringBuilder msg = new StringBuilder();
        msg.append("Số serial hợp lệ trong file (").append(validCount)
                .append(") không khớp với PO (yêu cầu ").append(expectedCount).append(").");
        if (!emptyRows.isEmpty()) {
            msg.append(" Có ").append(emptyRows.size())
                    .append(" dòng trống serial: dòng ")
                    .append(emptyRows.stream().map(String::valueOf).collect(Collectors.joining(", ")))
                    .append(".");
        }
        if (!blockedSerials.isEmpty()) {
            msg.append(" Có ").append(blockedSerials.size())
                    .append(" serial không hợp lệ: ")
                    .append(String.join("; ", blockedSerials))
                    .append(".");
        }
        return msg.toString();
    }

    private List<ReceiptDetail> parseDetailsStrict(String[] genIds, String[] serials,
            String[] detailNotes, List<String> errors) {
        List<ReceiptDetail> details = new ArrayList<>();
        if (genIds == null) {
            return details;
        }
        Map<String, Integer> firstSeenRow = new LinkedHashMap<>();
        for (int i = 0; i < genIds.length; i++) {
            String idStr = genIds[i];
            String serial = (serials != null && i < serials.length) ? serials[i] : null;
            String detailNote = (detailNotes != null && i < detailNotes.length) ? detailNotes[i] : null;
            boolean rowEmpty = (idStr == null || idStr.trim().isEmpty())
                    && (serial == null || serial.trim().isEmpty());
            if (rowEmpty) {
                continue;
            }
            int rowNum = i + 1;
            int genId = 0;
            try {
                genId = Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                errors.add("Dòng " + rowNum + ": Vui lòng chọn máy phát điện");
                continue;
            }
            if (genId <= 0) {
                errors.add("Dòng " + rowNum + ": Vui lòng chọn máy phát điện");
                continue;
            }

            if (serial != null) {
                serial = serial.trim();
                if (serial.isEmpty()) {
                    serial = null;
                } else if (serial.length() > MAX_SERIAL_LENGTH) {
                    errors.add("Dòng " + rowNum + ": Số serial không được vượt quá " + MAX_SERIAL_LENGTH + " ký tự");
                    continue;
                } else {
                    Integer firstRow = firstSeenRow.get(serial);
                    if (firstRow != null) {
                        errors.add("Dòng " + firstRow + " và dòng " + rowNum
                                + ": Số serial \"" + serial + "\" bị trùng trong phiếu");
                        continue;
                    }
                    firstSeenRow.put(serial, rowNum);
                }
            }
            if (serial == null) {
                errors.add("Dòng " + rowNum + ": Vui lòng nhập số serial");
                continue;
            }
            if (detailNote != null && detailNote.length() > MAX_NOTE_LENGTH) {
                errors.add("Dòng " + rowNum + ": Ghi chú không được vượt quá " + MAX_NOTE_LENGTH + " ký tự");
                continue;
            }
            ReceiptDetail d = new ReceiptDetail();
            d.setGeneratorId(genId);
            d.setSerialNumber(serial);
            d.setNote(detailNote);
            details.add(d);
        }
        return details;
    }

    private void notifySourceWarehouseStaff(Transfer transfer,
            Receipt receipt, User sender, String contextPath) {
        List<User> staff = userDAO.findUsersByPermission("receipts", "view");
        if (staff == null) {
            return;
        }
        for (User u : staff) {
            if (u.getId() == sender.getId()) {
                continue;
            }
            Integer scopedWh = null;
            try {
                scopedWh = new UserDAO().getScopedWarehouseId(u.getId());
            } catch (Exception ex) {
                continue;
            }
            if (scopedWh != null && scopedWh == transfer.getSourceWarehouseId()) {
                NotificationUtil.send(
                        u.getId(),
                        "Phiếu nhập đã hoàn tất",
                        "Kho đích đã tạo phiếu nhập " + receipt.getReceiptCode()
                        + " cho phiếu luân chuyển " + transfer.getTransferCode()
                        + ". Quá trình luân chuyển đã hoàn tất.",
                        contextPath + "/transfers?action=detail&id=" + transfer.getTransferId(),
                        "transfer",
                        transfer.getTransferId()
                );
            }
        }
    }
}
