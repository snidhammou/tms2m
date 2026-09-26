package com.tms.server.repository;

import com.tms.server.domain.Task;
import com.tms.server.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByTerminalIdAndStatusInOrderByIdAsc(Long terminalId, Collection<TaskStatus> statuses);

    List<Task> findTop200ByOrderByIdDesc();

    List<Task> findTop200ByStatusOrderByIdDesc(TaskStatus status);

    List<Task> findTop200ByTerminalIdOrderByIdDesc(Long terminalId);

    List<Task> findByDeploymentIdOrderByIdAsc(String deploymentId);

    @Query("select t.status, count(t) from Task t group by t.status")
    List<Object[]> countByStatus();

    @Modifying
    @Query("delete from Task t where t.terminal.id = :terminalId")
    void deleteByTerminalId(Long terminalId);
}
