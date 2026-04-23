package com.unq.dapp.bolsa.catalog.application;

import com.unq.dapp.bolsa.catalog.api.PlayerResponse;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public Page<PlayerResponse> list(League league, String team, Position position, Pageable pageable) {
        return playerRepository.findWithFilters(league, team, position, pageable)
                .map(PlayerResponse::from);
    }

}
