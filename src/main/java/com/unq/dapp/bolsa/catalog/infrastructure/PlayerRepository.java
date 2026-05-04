package com.unq.dapp.bolsa.catalog.infrastructure;

import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.domain.Position;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    @Query("SELECT p FROM Player p WHERE " +
           "(:league IS NULL OR p.league = :league) AND " +
           "(:team IS NULL OR LOWER(p.team) LIKE LOWER(CONCAT('%', :team, '%'))) AND " +
           "(:position IS NULL OR p.position = :position) AND " +
           "p.active = true")
    Page<Player> findWithFilters(
            @Param("league") League league,
            @Param("team") String team,
            @Param("position") Position position,
            Pageable pageable
    );

    Optional<Player> findByIdAndActiveTrue(Long id);
}
