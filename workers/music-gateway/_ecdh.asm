0x1401d0fc0 cmp rsp, qword ptr [r14 + 0x10]
0x1401d0fc4 jbe 0x1401d1056
0x1401d0fca push rbp
0x1401d0fcb mov rbp, rsp
0x1401d0fce sub rsp, 0x20
0x1401d0fd2 mov rdx, qword ptr [rax]
0x1401d0fd5 cmp qword ptr [rbx], rdx
0x1401d0fd8 jne 0x1401d0ffb
0x1401d0fda mov qword ptr [rsp + 0x30], rax
0x1401d0fdf mov qword ptr [rsp + 0x38], rbx
0x1401d0fe4 mov rsi, qword ptr [rax + 8]
0x1401d0fe8 mov rcx, qword ptr [rbx + 8]
0x1401d0fec mov rax, rdx
0x1401d0fef mov rbx, rsi
0x1401d0ff2 call 0x140018280
0x1401d0ff7 test al, al
0x1401d0ff9 jne 0x1401d1039
0x1401d0ffb mov eax, 0x10
0x1401d1000 lea rbx, [rip + 0x1bef4f9]
0x1401d1007 mov ecx, 1
0x1401d100c call 0x140022a00
0x1401d1011 mov qword ptr [rax + 8], 0x3b
0x1401d1019 lea rdx, [rip + 0x86c249]
0x1401d1020 mov qword ptr [rax], rdx
0x1401d1023 xor ebx, ebx
0x1401d1025 mov ecx, ebx
0x1401d1027 lea rdi, [rip + 0x1caf8da]
0x1401d102e mov rsi, rax
0x1401d1031 xor eax, eax
0x1401d1033 add rsp, 0x20
0x1401d1037 pop rbp
0x1401d1038 ret 
0x1401d1039 mov rbx, qword ptr [rsp + 0x30]
0x1401d103e mov rax, qword ptr [rbx + 8]
0x1401d1042 mov rdx, qword ptr [rbx]
0x1401d1045 mov rdx, qword ptr [rdx + 0x30]
0x1401d1049 mov rcx, qword ptr [rsp + 0x38]
0x1401d104e call rdx
0x1401d1050 add rsp, 0x20
0x1401d1054 pop rbp
0x1401d1055 ret 
0x1401d1056 mov qword ptr [rsp + 8], rax
0x1401d105b mov qword ptr [rsp + 0x10], rbx
0x1401d1060 call 0x14008c6e0
0x1401d1065 mov rax, qword ptr [rsp + 8]
0x1401d106a mov rbx, qword ptr [rsp + 0x10]
0x1401d106f jmp 0x1401d0fc0