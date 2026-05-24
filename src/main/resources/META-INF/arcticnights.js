var Opcodes=Java.type('org.objectweb.asm.Opcodes')
var InsnList=Java.type('org.objectweb.asm.tree.InsnList')
var VarInsnNode=Java.type('org.objectweb.asm.tree.VarInsnNode')
var MethodInsnNode=Java.type('org.objectweb.asm.tree.MethodInsnNode')
var ASM = Java.type('net.neoforged.coremod.api.ASMAPI');
var IllegalStateException = Java.type('java.lang.IllegalStateException');

function initializeCoreMod() {
    return {
        'arcn_level_skydarkness': {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.world.level.LevelReader'
            },
            'transformer': function(classNode) {
                var patched = false;
                for (var methodNode of classNode.methods) {
                    if (methodNode.name === ASM.mapMethod("getMaxLocalRawBrightness") && methodNode.desc === "(Lnet/minecraft/core/BlockPos;)I") {
                        var instructions = methodNode.instructions;
                        for (var insn of instructions) {
                            if (insn.getOpcode() === Opcodes.INVOKEINTERFACE
                                    && insn.owner === 'net/minecraft/world/level/LevelReader'
                                    && insn.name === ASM.mapMethod("getSkyDarken")
                                    && insn.desc === "()I") {
                                var patchList = new InsnList();
                                patchList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                patchList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, 'com/judiraal/arcticnights/util/Calculations', "seasonalSkyDarken", "(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I"));
                                instructions.insertBefore(insn, patchList);
                                instructions.remove(insn);
                                patched = true;
                                break;
                            }
                        }
                        break;
                    }
                }
                if (!patched) {
                    throw new IllegalStateException("Arctic Nights failed to patch LevelReader#getMaxLocalRawBrightness(BlockPos)");
                }

				return classNode;
            }
        }
    };
}
