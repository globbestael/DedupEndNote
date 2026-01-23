package edu.dedupendnote.domain;

import org.jspecify.annotations.Nullable;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StompMessage {

	@Nullable String name;

}
