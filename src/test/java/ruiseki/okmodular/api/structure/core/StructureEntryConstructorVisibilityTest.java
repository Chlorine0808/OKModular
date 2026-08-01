package ruiseki.okmodular.api.structure.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * StructureEntry の生成経路を公開 API から外したことを縛る（release_freeze F-4）。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * StructureEntry のコンストラクタは引数を 25 個取る。構造まわりの残った
 * ロードマップ項目（可変形成・Tier 要求・構造 IO）は**すべてこの引数を増やす**ので、
 * public のまま最初のリリースを出すと、機能を 1 つ足すたびに破壊的変更になる。
 *
 * 呼び出し元は同一パッケージの StructureEntryBuilder ただ 1 つなので、
 * public を外しても mod 内部には一切影響が無い。**外に出す前の今しか外せない。**
 *
 * ============================================
 * このテストが見ているもの
 * ============================================
 *
 * 「public でないこと」だけを見る。package-private / protected / private の
 * どれであるかは問わない（builder が同一パッケージにいるので package-private が
 * 自然だが、そこを縛ると将来の内部整理を無意味に妨げる）。
 *
 * 併せて **builder 側の入口が公開されたままであること**を見る。
 * 生成経路を閉じるだけで代わりの入口が無いと、公開 API として
 * 「構造を作れない」状態になるため。
 */
@DisplayName("StructureEntry の生成経路")
public class StructureEntryConstructorVisibilityTest {

    @Test
    @DisplayName("public なコンストラクタを 1 つも持たない")
    public void testコンストラクタが公開されていない() {
        for (Constructor<?> constructor : StructureEntry.class.getDeclaredConstructors()) {
            assertFalse(
                Modifier.isPublic(constructor.getModifiers()),
                () -> "StructureEntry に public なコンストラクタが残っている（引数 " + constructor.getParameterCount()
                    + " 個）。公開したままリリースすると、構造の新機能で引数を増やすたびに破壊的変更になる。"
                    + "生成は StructureEntryBuilder に通すこと");
        }
    }

    @Test
    @DisplayName("生成の入口は StructureEntryBuilder.build() で、そちらは公開されている")
    public void testビルダの入口は公開されている() throws NoSuchMethodException {
        Method build = StructureEntryBuilder.class.getMethod("build");

        assertTrue(
            Modifier.isPublic(build.getModifiers()),
            "StructureEntryBuilder.build() が公開されていない。コンストラクタを閉じた以上、" + "これが唯一の生成経路なので公開されていなければならない");

        assertEquals(
            IStructureEntry.class,
            build.getReturnType(),
            "build() の戻り値が IStructureEntry でない。実装型を返すと、" + "コンストラクタを閉じた意味が戻り値の型から漏れる");
    }
}
