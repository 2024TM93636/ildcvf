package com.ildcvf.contract.version;

public class ContractVersionNotFoundException
        extends RuntimeException {

    public ContractVersionNotFoundException(String message) {
        super(message);
    }
}
