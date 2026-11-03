package com.matheus.orderFlow.payment;

record ChargeResponse(String id, String status, String reason) {

    boolean approved() {
        return "approved".equalsIgnoreCase(status);
    }
}
