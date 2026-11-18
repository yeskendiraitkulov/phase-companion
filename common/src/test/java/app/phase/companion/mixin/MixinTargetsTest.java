package app.phase.companion.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Миксины мода встают на классы этой версии игры — проверка без запуска игры (Forge и
 * NeoForge: их загрузчик не запускается в JUnit так просто, как Fabric).
 *
 * <p>Читает байт-код миксинов и классов игры (имена Mojang — те же, что у Forge и NeoForge в
 * игре) и сверяет: класс-цель есть, у {@code @Inject} есть метод-цель с теми же параметрами,
 * у {@code @Accessor} есть поле того же типа. И что каждый миксин из пакета записан в
 * {@code phase_companion.mixins.json}, а каждый записанный — существует.
 */
class MixinTargetsTest {
	private static final String PACKAGE = "app/phase/companion/mixin/";
	private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";

	@Test
	void configListsEveryMixin() throws Exception {
		JsonObject config = config();
		assertEquals("app.phase.companion.mixin", config.get("package").getAsString());
		assertTrue(config.get("required").getAsBoolean(), "required");
		assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt(), "defaultRequire");
		assertEquals(compiledMixins(), listed(config), "миксины в пакете и в phase_companion.mixins.json");
	}

	@Test
	void everyTargetExistsInTheGame() throws Exception {
		int checked = 0;
		for (String name : listed(config())) {
			ClassNode mixin = read(PACKAGE + name);
			List<Type> targets = targets(mixin);
			assertFalse(targets.isEmpty(), name + ": нет @Mixin");
			for (Type target : targets) {
				ClassNode game = read(target.getInternalName());
				for (MethodNode method : mixin.methods) {
					AnnotationNode inject = annotation(method, INJECT);
					if (inject != null) {
						for (String wanted : strings(inject, "method")) {
							assertInjectTarget(name, game, wanted, method);
							checked++;
						}
					}
					AnnotationNode accessor = annotation(method, ACCESSOR);
					if (accessor != null) {
						assertAccessorTarget(name, game, accessor, method);
						checked++;
					}
				}
			}
		}
		assertTrue(checked > 0, "ни одной точки миксина не проверено");
	}

	private static void assertInjectTarget(String mixin, ClassNode game, String wanted, MethodNode handler) {
		String targetName = wanted.contains("(") ? wanted.substring(0, wanted.indexOf('(')) : wanted;
		// Параметры обработчика: параметры цели и в конце CallbackInfo(Returnable).
		Type[] handlerArgs = Type.getArgumentTypes(handler.desc);
		Type[] expected = Arrays.copyOf(handlerArgs, handlerArgs.length - 1);
		String callback = handlerArgs[handlerArgs.length - 1].getInternalName();
		assertTrue(callback.startsWith("org/spongepowered/asm/mixin/injection/callback/CallbackInfo"),
				mixin + "." + handler.name + ": последний параметр — CallbackInfo");
		boolean found = game.methods.stream()
				.filter(m -> m.name.equals(targetName))
				.anyMatch(m -> Arrays.equals(Type.getArgumentTypes(m.desc), expected));
		assertTrue(found, mixin + ": в " + game.name + " нет " + targetName
				+ Arrays.stream(expected).map(Type::getClassName).collect(Collectors.joining(", ", "(", ")")));
	}

	private static void assertAccessorTarget(String mixin, ClassNode game, AnnotationNode accessor, MethodNode method) {
		List<String> names = strings(accessor, "value");
		assertEquals(1, names.size(), mixin + "." + method.name + ": имя поля в @Accessor");
		Type[] args = Type.getArgumentTypes(method.desc);
		Type type = args.length == 0 ? Type.getReturnType(method.desc) : args[0];
		FieldNode field = game.fields.stream().filter(f -> f.name.equals(names.get(0))).findFirst().orElse(null);
		assertNotNull(field, mixin + ": в " + game.name + " нет поля " + names.get(0));
		assertEquals(type.getDescriptor(), field.desc, mixin + ": тип поля " + names.get(0));
	}

	// ------------------------------------------------------------------ чтение

	private static JsonObject config() throws IOException {
		try (InputStream in = resource("phase_companion.mixins.json")) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static Set<String> listed(JsonObject config) {
		Set<String> names = new TreeSet<>();
		for (String section : List.of("mixins", "client", "server")) {
			if (config.has(section)) {
				config.getAsJsonArray(section).forEach(e -> names.add(e.getAsString()));
			}
		}
		return names;
	}

	/** Миксины, собранные в этот проект: все классы пакета, кроме вложенных. */
	private static Set<String> compiledMixins() throws IOException, URISyntaxException {
		URL known = MixinTargetsTest.class.getClassLoader().getResource(PACKAGE + "MinecraftServerMixin.class");
		assertNotNull(known, "MinecraftServerMixin не собран");
		Path dir = Path.of(known.toURI()).getParent();
		try (Stream<Path> files = Files.list(dir)) {
			return files.map(p -> p.getFileName().toString())
					.filter(n -> n.endsWith(".class") && !n.contains("$"))
					.map(n -> n.substring(0, n.length() - ".class".length()))
					.filter(n -> !n.equals("MixinTargetsTest"))
					.collect(Collectors.toCollection(TreeSet::new));
		}
	}

	private static ClassNode read(String internalName) throws IOException {
		try (InputStream in = resource(internalName + ".class")) {
			ClassNode node = new ClassNode();
			new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
			return node;
		}
	}

	private static InputStream resource(String path) {
		InputStream in = MixinTargetsTest.class.getClassLoader().getResourceAsStream(path);
		assertNotNull(in, "нет на classpath: " + path);
		return in;
	}

	private static AnnotationNode annotation(MethodNode method, String desc) {
		return Stream.of(method.visibleAnnotations, method.invisibleAnnotations)
				.filter(list -> list != null)
				.flatMap(List::stream)
				.filter(a -> a.desc.equals(desc))
				.findFirst()
				.orElse(null);
	}

	private static List<Type> targets(ClassNode mixin) {
		List<Type> targets = new ArrayList<>();
		Stream.of(mixin.visibleAnnotations, mixin.invisibleAnnotations)
				.filter(list -> list != null)
				.flatMap(List::stream)
				.filter(a -> a.desc.equals(MIXIN))
				.forEach(a -> values(a, "value").forEach(v -> targets.add((Type) v)));
		return targets;
	}

	private static List<String> strings(AnnotationNode annotation, String key) {
		return values(annotation, key).stream().map(String.class::cast).toList();
	}

	/** Значение поля аннотации: массив или одно значение — всегда списком. */
	private static List<Object> values(AnnotationNode annotation, String key) {
		if (annotation.values == null) {
			return List.of();
		}
		for (int i = 0; i < annotation.values.size(); i += 2) {
			if (annotation.values.get(i).equals(key)) {
				Object value = annotation.values.get(i + 1);
				return value instanceof List<?> list ? new ArrayList<>(list) : List.of(value);
			}
		}
		return List.of();
	}
}
