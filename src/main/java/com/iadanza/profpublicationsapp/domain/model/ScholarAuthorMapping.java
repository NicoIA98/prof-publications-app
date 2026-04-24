package com.iadanza.profpublicationsapp.domain.model;

/**
 * Associa un professore locale a uno specifico Google Scholar author id.
 * Serve perché l'identificazione automatica del profilo Scholar non è sempre affidabile.
 */
public record ScholarAuthorMapping(
        String professorFullName,
        String affiliation,
        String scholarAuthorId,
        boolean manuallyVerified
) {
}