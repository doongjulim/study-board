package com.example.board.post.repository;

import com.example.board.post.domain.AttachedFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttachedFileRepository extends JpaRepository<AttachedFile, Long> {

    Optional<AttachedFile> findByStoredName(String storedName);
}
